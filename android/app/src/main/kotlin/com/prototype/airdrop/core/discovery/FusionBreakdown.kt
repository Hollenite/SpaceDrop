package com.prototype.airdrop.core.discovery



data class FusionBreakdown(

    val bleRssiWeight: Float,

    val mdnsWeight: Float,

    val agreementWeight: Float,

    val bssidWeight: Float,

    val hostWeight: Float,

    val rssi: Int?,

    val hasBle: Boolean,

    val hasMdns: Boolean,

    val bssidMatch: Boolean,

    val signalSummary: String,

    val phoneBssid: String? = null,

    val pcBssid: String? = null

) {

    /** Legacy fields for debug lab compatibility. */

    val baseConfidence: Float get() = bleRssiWeight + mdnsWeight

    val mdnsBoost: Float get() = agreementWeight + bssidWeight + hostWeight

}


