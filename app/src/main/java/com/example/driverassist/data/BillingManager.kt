package com.example.driverassist.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages Google Play Billing lifecycle and purchases.
 */
class BillingManager(
    private val context: Context,
    private val externalScope: CoroutineScope
) : PurchasesUpdatedListener {

    private val _isVerified = MutableStateFlow(false)
    val isVerified: StateFlow<Boolean> = _isVerified

    private val _subscriptionPrice = MutableStateFlow<String?>(null)
    val subscriptionPrice: StateFlow<String?> = _subscriptionPrice

    private var billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var verifiedProductDetails: ProductDetails? = null

    companion object {
        const val VERIFIED_SUB_ID = "verified_user_monthly"
    }

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Billing setup successful")
                    querySubscriptionDetails()
                    queryPurchases()
                } else {
                    Log.e("BillingManager", "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Billing service disconnected")
            }
        })
    }

    private fun querySubscriptionDetails() {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(VERIFIED_SUB_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams, object : ProductDetailsResponseListener {
            override fun onProductDetailsResponse(billingResult: BillingResult, result: QueryProductDetailsResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val product = result.productDetailsList.find { it.productId == VERIFIED_SUB_ID }
                    verifiedProductDetails = product
                    
                    product?.subscriptionOfferDetails?.firstOrNull()?.let { offer ->
                        val phases = offer.pricingPhases.pricingPhaseList
                        _subscriptionPrice.value = phases.firstOrNull()?.formattedPrice
                    }
                } else {
                    Log.e("BillingManager", "Query Product Details failed: ${billingResult.debugMessage}")
                }
            }
        })
    }

    fun queryPurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params, object : PurchasesResponseListener {
            override fun onQueryPurchasesResponse(billingResult: BillingResult, purchases: List<Purchase>) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchases)
                } else {
                    Log.e("BillingManager", "Query Purchases failed: ${billingResult.debugMessage}")
                }
            }
        })
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingManager", "User cancelled purchase")
        } else {
            Log.e("BillingManager", "Purchases updated error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val isVerifiedNow = purchases.any { purchase ->
            purchase.products.contains(VERIFIED_SUB_ID) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        
        _isVerified.value = isVerifiedNow

        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingManager", "Purchase acknowledged")
            } else {
                Log.e("BillingManager", "Acknowledge Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchUpgradeFlow(activity: Activity) {
        val productDetails = verifiedProductDetails ?: run {
            Log.e("BillingManager", "Product details not available")
            return
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            Log.e("BillingManager", "Offer token not available")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }
}
