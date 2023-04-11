package xyz.cssxsh.mirai.hibernate.forward

import kotlinx.coroutines.*
import net.mamoe.mirai.console.permission.PermissionService.Companion.testPermission
import net.mamoe.mirai.console.permission.PermitteeId.Companion.permitteeId
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.util.ContactUtils.render
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.hibernate.*
import xyz.cssxsh.mirai.hibernate.*
import xyz.cssxsh.mirai.hibernate.entry.*
import java.util.*
import java.util.stream.*

public object HibernateForwardExtension : KotlinPlugin(
    JvmPluginDescription(
        id = "xyz.cssxsh.mirai.plugin.mirai-hibernate-forward",
        name = "mirai-hibernate-forward",
        version = "0.1.1",
    ) {
        author("cssxsh")

        dependsOn("xyz.cssxsh.mirai.plugin.mirai-hibernate-plugin", ">= 2.5.0")
    }
) {
    private val at: MutableMap<Long, Int> = WeakHashMap()
    private val recall: MutableMap<Long, Int> = WeakHashMap()

    private fun records(group: Group, since: Int): Stream<MessageRecord> {
        val session = factory.openSession()
        return try {
            session.withCriteria<MessageRecord> { query ->
                val record = query.from<MessageRecord>()
                query.select(record)
                    .where(
                        equal(record.get<Int>("bot"), group.bot.id),
                        equal(record.get<MessageSourceKind>("kind"), MessageSourceKind.GROUP),
                        equal(record.get<Long>("targetId"), group.id),
                        le(record.get<Int>("time"), since)
                    )
                    .orderBy(desc(record.get<Int>("time")))
            }.stream().onClose { session.close() }
        } catch (cause: Throwable) {
            session.close()
            throw cause
        }
    }

    override fun onEnable() {
        globalEventChannel().subscribeGroupMessages {
            """(?i)谁@我|谁AT我""".toRegex() findingReply query@{ _ ->
                if (parentPermission.testPermission(sender.permitteeId).not()) return@query null
                logger.info("Query At For ${sender.render()}")
                val key = subject.id xor sender.id
                val record = records(group = subject, since = at[key] ?: time).use { stream ->
                    stream.filter { record ->
                        val chain = record.toMessageChain()
                        chain.findIsInstance<At>()?.target == sender.id
                    }.findFirst().orElse(null)
                } ?: return@query null
                at[key] = record.time

                val prev = MiraiHibernateRecorder[subject, record.time - 900, record.time - 1]
                val next = MiraiHibernateRecorder[subject, record.time, record.time + 900]
                val records = buildList {
                    addAll(prev.subList(0, prev.size.coerceAtMost(9)).asReversed())
                    addAll(next.asReversed().subList(0, next.size.coerceAtMost(9)))
                }

                at[key] = records.minOfOrNull { it.time } ?: record.time

                records.toForwardMessage(subject)
            }
            """(?i)谁撤回了|谁撤销了""".toRegex() findingReply query@{ _ ->
                if (parentPermission.testPermission(sender.permitteeId).not()) return@query null
                logger.info("Query Recall For ${subject.render()}")
                val record = records(group = subject, since = recall[subject.id] ?: time).use { stream ->
                    stream.filter { record ->
                        record.recall && record.fromId != sender.id
                    }.findFirst().orElse(null)
                } ?: return@query null
                recall[subject.id] = record.time

                val prev = MiraiHibernateRecorder[subject, record.time - 300, record.time - 1]
                val next = MiraiHibernateRecorder[subject, record.time, record.time + 300]
                val records = buildList {
                    addAll(prev.subList(0, prev.size.coerceAtMost(3)).asReversed())
                    addAll(next.asReversed().subList(0, next.size.coerceAtMost(3)))
                }

                recall[subject.id] = records.minOfOrNull { it.time } ?: record.time

                records.toForwardMessage(subject)
            }
        }
    }

    override fun onDisable() {
        coroutineContext.cancelChildren()
    }
}