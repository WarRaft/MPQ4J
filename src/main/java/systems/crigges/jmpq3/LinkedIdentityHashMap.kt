package systems.crigges.jmpq3

import java.util.*

/**
 *
 */
class LinkedIdentityHashMap<K, V> : IdentityHashMap<K?, V?>(), Iterable<K?> {
    private val order = LinkedList<K?>()

    override fun put(key: K?, value: V?): V? {
        val oldValue = super.put(key, value)
        if (oldValue == null) {
            if (value != null) {
                order.add(key)
            }
        } else {
            if (value == null) {
                order.remove(key)
            }
        }
        return oldValue
    }

    override fun iterator(): MutableIterator<K?> {
        return order.iterator()
    }

    fun first(): K? {
        return order.first()
    }

    fun last(): K? {
        return order.last()
    }

    override fun equals(other: Any?): Boolean {
        if (other is LinkedIdentityHashMap<*, *>) {
            val map = other
            if (order.size != map.order.size) {
                return false
            }
            val iterator: MutableIterator<*> = map.order.iterator()
            for (key in order) {
                if (key !== iterator.next() || get(key) != map[key]) {
                    return false
                }
            }
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        return order.hashCode()
    }


    override fun remove(key: K?, value: V?): Boolean {
        order.remove(key)
        return super.remove(key, value)
    }

    override fun remove(key: K?): V? {
        order.remove(key)
        return super.remove(key)
    }

}
