package app.pwhs.blockads.service

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Manages VPN connection retry logic with exponential backoff.
 */
class VpnRetryManager(
    private val maxRetries: Int = 5,
    private val maxDelayMs: Long = 60000L
) {

    private var retryCount = 0
    private var lastAttemptTime = 0L

    /**
     * Reset retry counter.
     */
    fun reset() {
        retryCount = 0
        lastAttemptTime = 0L
        Timber.d("Retry counter reset")
    }

    /**
     * Check if should retry based on current retry count.
     */
    fun shouldRetry(): Boolean {
        return retryCount < maxRetries
    }

    /**
     * Get current retry count.
     */
    fun getRetryCount(): Int = retryCount

    /**
     * Get maximum retry attempts.
     */
    fun getMaxRetries(): Int = maxRetries

    /**
     * Calculate and wait for the exponential backoff delay before next retry.
     * Returns true if waiting completed successfully, false if interrupted.
     */
    /**
     * Gentle Fibonacci-like delays: 1s, 1s, 2s, 3s, 5s
     * Total worst-case wait = 12s (vs 31s with exponential backoff).
     */
    private val delaySteps = longArrayOf(1000L, 1000L, 2000L, 3000L, 5000L)

    suspend fun waitForRetry(): Boolean {
        if (!shouldRetry()) {
            Timber.w("Max retries ($maxRetries) reached")
            return false
        }

        retryCount++
        lastAttemptTime = System.currentTimeMillis()

        // Use gentle backoff curve instead of exponential
        val delayMs = minOf(
            delaySteps.getOrElse(retryCount - 1) { delaySteps.last() },
            maxDelayMs
        )

        Timber.d("Retry attempt $retryCount/$maxRetries - waiting ${delayMs}ms before retry")

        try {
            delay(delayMs)
            return true
        } catch (e: Exception) {
            Timber.e("Retry wait interrupted: $e")
            return false
        }
    }
}
