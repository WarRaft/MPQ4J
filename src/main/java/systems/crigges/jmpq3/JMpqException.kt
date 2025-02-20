/*
 * 
 */
package systems.crigges.jmpq3

import java.io.IOException

/**
 * The Class JMpqException.
 */
class JMpqException : IOException {
    /**
     * Instantiates a new j mpq exception.
     *
     * @param msg the msg
     */
    constructor(msg: String?) : super(msg)

    /**
     * Instantiates a new j mpq exception.
     *
     * @param t the t
     */
    internal constructor(t: Throwable?) : super(t)

    companion object {
        /**
         * The Constant serialVersionUID.
         */
        private const val serialVersionUID = 1L
    }
}
