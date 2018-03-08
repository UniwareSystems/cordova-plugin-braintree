package net.justincredible;

import android.app.Activity;
import android.content.Intent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.DataCollector;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.dropin.DropInRequest;
import com.braintreepayments.api.dropin.DropInResult;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.models.CardNonce;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.ThreeDSecureInfo;
import com.braintreepayments.api.models.VenmoAccountNonce;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.LineItem;

import java.util.HashMap;
import java.util.Map;

public final class BraintreePlugin extends CordovaPlugin implements PaymentMethodNonceCreatedListener {

    private static final int DROP_IN_REQUEST = 100;
    private static final int PAYMENT_BUTTON_REQUEST = 200;
    private static final int CUSTOM_REQUEST = 300;
    private static final int PAYPAL_REQUEST = 400;

    private DropInRequest dropInRequest = null;
    private CallbackContext _callbackContext = null;
    private BraintreeFragment braintreeFragment = null;

    @Override
    public synchronized boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        if (action == null) {
            return false;
        }

        _callbackContext = callbackContext;

        try {
            if (action.equals("initialize")) {
                this.initialize(args);
            }
            else if (action.equals("presentDropInPaymentUI")) {
                this.presentDropInPaymentUI(args);
            }
            else if (action.equals("payPalProcess")) {
                this.payPalProcess(args);
            }
            else if (action.equals("payPalProcessVaulted")) {
                this.payPalProcessVaulted();
            }
            else {
                // The given action was not handled above.
                return false;
            }
        }
            catch (Exception exception) {
            callbackContext.error("BraintreePlugin uncaught exception: " + exception.getMessage());
        }

        return true;
    }

    // Actions

    private synchronized void initialize(final JSONArray args) throws Exception {

        // Ensure we have the correct number of arguments.
        if (args.length() != 1) {
            _callbackContext.error("A token is required.");
            return;
        }

        // Obtain the arguments.
        String token = args.getString(0);

        if (token == null || token.equals("")) {
            _callbackContext.error("A token is required.");
            return;
        }

        dropInRequest = new DropInRequest().clientToken(token);

        if (dropInRequest == null) {
            _callbackContext.error("The Braintree client failed to initialize.");
            return;
        }

        braintreeFragment = BraintreeFragment.newInstance(this.cordova.getActivity(), token);
        braintreeFragment.addListener(this);

        _callbackContext.success();
    }

    private synchronized void setupApplePay(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // Apple Pay available on iOS only
        callbackContext.success();
    }

    private synchronized void presentDropInPaymentUI(final JSONArray args) throws JSONException {

        // Ensure the client has been initialized.
        if (dropInRequest == null) {
            _callbackContext.error("The Braintree client must first be initialized via BraintreePlugin.initialize(token)");
            return;
        }

        // Ensure we have the correct number of arguments.
        if (args.length() < 1) {
            _callbackContext.error("amount is required.");
            return;
        }

        // Obtain the arguments.

        String amount = args.getString(0);
        
        if (amount == null) {
            _callbackContext.error("amount is required.");
        }
        
        String primaryDescription = args.getString(1);

        dropInRequest.amount(amount);

        // TODO: Make this conditional
        dropInRequest.androidPayCart(Cart.newBuilder()
            .setCurrencyCode("GBP")
            .setTotalPrice(amount)
            .addLineItem(LineItem.newBuilder()
                .setCurrencyCode("GBP")
                .setDescription(primaryDescription)
                .setQuantity("1")
                .setUnitPrice(amount)
                .setTotalPrice(amount)
                .build())
            .build()
        );

        this.cordova.setActivityResultCallback(this);
        this.cordova.startActivityForResult(this, dropInRequest.getIntent(this.cordova.getActivity()), DROP_IN_REQUEST);
    }

    private synchronized void payPalProcess(final JSONArray args) throws Exception {
        PayPalRequest payPalRequest = new PayPalRequest(args.getString(0));
        payPalRequest.currencyCode(args.getString(1));
        PayPal.requestOneTimePayment(braintreeFragment, payPalRequest);
    }
    private synchronized void payPalProcessVaulted() throws Exception {
        PayPal.authorizeAccount(braintreeFragment);
    }

    // Results

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (_callbackContext == null) {
            return;
        }

        if (requestCode == DROP_IN_REQUEST) {

            PaymentMethodNonce paymentMethodNonce = null;

            if (resultCode == Activity.RESULT_OK) {
                DropInResult result = intent.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);
                paymentMethodNonce = result.getPaymentMethodNonce();
            }

            this.handleDropInPaymentUiResult(resultCode, paymentMethodNonce);
        }
        else if (requestCode == PAYMENT_BUTTON_REQUEST) {
            //TODO
            _callbackContext.error("Activity result handler for PAYMENT_BUTTON_REQUEST not implemented.");
        }
        else if (requestCode == CUSTOM_REQUEST) {
            _callbackContext.error("Activity result handler for CUSTOM_REQUEST not implemented.");
            //TODO
        }
        else if (requestCode == PAYPAL_REQUEST) {
            _callbackContext.error("Activity result handler for PAYPAL_REQUEST not implemented.");
            //TODO
        }
    }

    /**
     * Helper used to handle the result of the drop-in payment UI.
     *
     * @param resultCode Indicates the result of the UI.
     * @param paymentMethodNonce Contains information about a successful payment.
     */
    private void handleDropInPaymentUiResult(int resultCode, PaymentMethodNonce paymentMethodNonce) {

        if (_callbackContext == null) {
            return;
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            Map<String, Object> resultMap = new HashMap<String, Object>();
            resultMap.put("userCancelled", true);
            _callbackContext.success(new JSONObject(resultMap));
            _callbackContext = null;
            return;
        }

        if (paymentMethodNonce == null) {
            _callbackContext.error("Result was not RESULT_CANCELED, but no PaymentMethodNonce was returned from the Braintree SDK.");
            _callbackContext = null;
            return;
        }

        Map<String, Object> resultMap = this.getPaymentUINonceResult(paymentMethodNonce);
        _callbackContext.success(new JSONObject(resultMap));
        _callbackContext = null;
    }

    /**
     * Helper used to return a dictionary of values from the given payment method nonce.
     * Handles several different types of nonces (eg for cards, PayPal, etc).
     *
     * @param paymentMethodNonce The nonce used to build a dictionary of data from.
     * @return The dictionary of data populated via the given payment method nonce.
     */
    private Map<String, Object> getPaymentUINonceResult(PaymentMethodNonce paymentMethodNonce) {

        Map<String, Object> resultMap = new HashMap<String, Object>();

        resultMap.put("nonce", paymentMethodNonce.getNonce());
        resultMap.put("type", paymentMethodNonce.getTypeLabel());
        resultMap.put("localizedDescription", paymentMethodNonce.getDescription());

        // Card
        if (paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce)paymentMethodNonce;

            Map<String, Object> innerMap = new HashMap<String, Object>();
            innerMap.put("lastTwo", cardNonce.getLastTwo());
            innerMap.put("network", cardNonce.getCardType());

            resultMap.put("card", innerMap);
        }

        // PayPal
        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce payPalAccountNonce = (PayPalAccountNonce)paymentMethodNonce;

            Map<String, Object> innerMap = new HashMap<String, Object>();
            resultMap.put("email", payPalAccountNonce.getEmail());
            resultMap.put("firstName", payPalAccountNonce.getFirstName());
            resultMap.put("lastName", payPalAccountNonce.getLastName());
            resultMap.put("phone", payPalAccountNonce.getPhone());
            //resultMap.put("billingAddress", payPalAccountNonce.getBillingAddress()); //TODO
            //resultMap.put("shippingAddress", payPalAccountNonce.getShippingAddress()); //TODO
            resultMap.put("clientMetadataId", payPalAccountNonce.getClientMetadataId());
            resultMap.put("payerId", payPalAccountNonce.getPayerId());

            resultMap.put("payPalAccount", innerMap);
        }

        // 3D Secure
        if (paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce) paymentMethodNonce;
            ThreeDSecureInfo threeDSecureInfo = cardNonce.getThreeDSecureInfo();

            if (threeDSecureInfo != null) {
                Map<String, Object> innerMap = new HashMap<String, Object>();
                innerMap.put("liabilityShifted", threeDSecureInfo.isLiabilityShifted());
                innerMap.put("liabilityShiftPossible", threeDSecureInfo.isLiabilityShiftPossible());

                resultMap.put("threeDSecureCard", innerMap);
            }
        }

        // Venmo
        if (paymentMethodNonce instanceof VenmoAccountNonce) {
            VenmoAccountNonce venmoAccountNonce = (VenmoAccountNonce) paymentMethodNonce;

            Map<String, Object> innerMap = new HashMap<String, Object>();
            innerMap.put("username", venmoAccountNonce.getUsername());

            resultMap.put("venmoAccount", innerMap);
        }

        return resultMap;
    }

    @Override
    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        try {
            JSONObject json = new JSONObject();

            json.put("nonce", paymentMethodNonce.getNonce().toString());
            json.put("deviceData", DataCollector.collectDeviceData(braintreeFragment));

            if (paymentMethodNonce instanceof PayPalAccountNonce) {
                PayPalAccountNonce pp = (PayPalAccountNonce) paymentMethodNonce;
                json.put("payerId", pp.getPayerId().toString());
                json.put("forename", pp.getFirstName().toString());
                json.put("surname", pp.getLastName().toString());
            }

            _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, json));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
