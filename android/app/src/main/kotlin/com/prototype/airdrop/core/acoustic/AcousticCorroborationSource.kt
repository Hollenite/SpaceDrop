package com.prototype.airdrop.core.acoustic

/**
 * Read-only view of the acoustic subsystem for the discovery fusion pipeline.
 *
 * Acoustic is a *corroboration-only* signal: it can confirm that a device already found via
 * BLE/mDNS is physically in the room (its speaker token was heard), but it must NEVER create a
 * device row on its own. Fusion resolves matches per candidate deviceId so the token math stays
 * in one place (this source owns the recently-decoded token set + freshness/epoch tolerance).
 */
interface AcousticCorroborationSource {
    /**
     * Of the given candidate device IDs, which ones have a currently-fresh acoustic token match
     * (computed for the current epoch and +/-1 for clock-skew tolerance).
     */
    fun matchingDeviceIds(candidateDeviceIds: Collection<String>): Set<String>
}
