// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

internal fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun responseFrame(command: Int, vararg payload: Int): ByteArray {
    val body = byteArrayOf(0xa1.toByte(), command.toByte(), (payload.size + 3).toByte()) +
        payload.map { it.toByte() }.toByteArray()
    val crc = QcProtocol.crc(body)
    return body + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
}

/** No sleeping: timers execute at their scheduled instant, in insertion order. */
internal class FakeClock : SessionClock {
    private data class Task(val at: Long, val order: Int, val action: () -> Unit, var cancelled: Boolean = false)
    private val tasks = mutableListOf<Task>()
    private var instant = 0L
    private var sequence = 0
    override fun now() = instant
    override fun later(delay: Long, action: () -> Unit): Cancellation {
        require(delay >= 0)
        val task = Task(instant + delay, sequence++, action)
        tasks += task
        return Cancellation { task.cancelled = true }
    }
    fun runCurrent() = advanceBy(0)
    fun advanceBy(amount: Long) {
        require(amount >= 0)
        val target = instant + amount
        var iterations = 0
        while (true) {
            val task = tasks.filter { !it.cancelled && it.at <= target }
                .minWithOrNull(compareBy<Task> { it.at }.thenBy { it.order }) ?: break
            check(++iterations < 1000) { "Timer did not settle" }
            tasks.remove(task)
            instant = task.at
            task.action()
        }
        tasks.removeAll { it.cancelled }
        instant = target
    }
    val hasTimers get() = tasks.any { !it.cancelled }
}

/** ACKs and notifications are separate so tests can reverse their arrival order. */
internal class FakeLink(private val clock: FakeClock) : SessionLink {
    data class Write(val bytes: ByteArray, val at: Long, val complete: (Boolean) -> Unit, var acknowledged: Boolean = false)
    val writes = mutableListOf<Write>()
    val events = mutableListOf<String>()
    var closeCount = 0
        private set
    private lateinit var receive: (ByteArray) -> Unit
    private lateinit var subscribed: (Boolean) -> Unit
    override fun subscribe(receive: (ByteArray) -> Unit, complete: (Boolean) -> Unit) {
        events += "subscribe"
        this.receive = receive
        subscribed = complete
    }
    override fun write(bytes: ByteArray, complete: (Boolean) -> Unit) {
        events += "write"
        writes += Write(bytes.copyOf(), clock.now(), complete)
    }
    override fun close() { closeCount++ }
    fun finishSubscription(ok: Boolean = true) = subscribed(ok)
    fun notify(bytes: ByteArray) = receive(bytes)
    fun acknowledge(index: Int, ok: Boolean = true) {
        val write = writes[index]
        check(!write.acknowledged)
        write.acknowledged = true
        write.complete(ok)
    }
    val pendingIndex get() = writes.indexOfFirst { !it.acknowledged }
}

internal class SessionRig {
    val clock = FakeClock()
    val statuses = mutableListOf<Pair<String, Boolean>>()
    val errors = mutableListOf<String>()
    val session = LightSession(clock, { text, ready -> statuses += text to ready }, { errors += it })
    val link = FakeLink(clock)
    init { session.attach(link) }
    fun authenticate(target: FakeLink = link) {
        target.finishSubscription()
        clock.runCurrent()
        target.acknowledge(0)
        target.notify(responseFrame(0, 67, 67, 72, 73, 80, 0x12, 0x34))
        clock.advanceBy(40)
        target.acknowledge(1)
        target.notify(responseFrame(1, 67, 67, 72, 73, 80, 0))
        check(session.ready)
    }
    fun drain(target: FakeLink = link) {
        repeat(100) {
            val pending = target.pendingIndex
            if (pending >= 0) target.acknowledge(pending)
            if (session.queuedActions == 0 && target.pendingIndex < 0 && !clock.hasTimers) return
            clock.advanceBy(40)
        }
        error("Session did not settle")
    }
    fun lightingHex(target: FakeLink = link) = target.writes.drop(2).map { it.bytes.hex() }
}
