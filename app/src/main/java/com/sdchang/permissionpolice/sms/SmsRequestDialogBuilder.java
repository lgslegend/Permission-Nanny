package com.sdchang.permissionpolice.sms;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewStub;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.sdchang.permissionpolice.BaseDialogBuilder;
import com.sdchang.permissionpolice.C;
import com.sdchang.permissionpolice.R;
import com.sdchang.permissionpolice.lib.Police;
import com.sdchang.permissionpolice.lib.request.sms.SmsRequest;
import org.apache.http.protocol.HTTP;

/**
 *
 */
public class SmsRequestDialogBuilder extends BaseDialogBuilder<SmsRequest> {

    private SmsOperation mOperation;

    @InjectView(R.id.tvReason) TextView tvReason;

    public SmsRequestDialogBuilder(Activity activity, Bundle args) {
        super(activity, args);
        for (SmsOperation operation : SmsOperation.operations) {
            if (operation.mOpCode.equals(mRequest.opCode())) {
                mOperation = operation;
                break;
            }
        }
    }

    @Override
    protected CharSequence buildDialogTitle(CharSequence appLabel) {
        SpannableStringBuilder boldAppLabel = new SpannableStringBuilder(appLabel);
        boldAppLabel.setSpan(new StyleSpan(Typeface.BOLD), 0, appLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return boldAppLabel.append(C.SPACE).append(mActivity.getText(mOperation.mDialogTitle));
    }

    @Override
    public void inflateViewStub(ViewStub stub) {
        stub.setLayoutResource(R.layout.dialog_text);
        View view = stub.inflate();
        ButterKnife.inject(this, view);
        tvReason.setText(mReason);
    }

    @Override
    protected Intent onAllowRequest() {
        SmsManager sms = SmsManager.getDefault();
        Bundle response = new Bundle();
        mOperation.mFunction.execute(sms, mRequest, response);
        return super.onAllowRequest()
                .putExtra(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE)
                .putExtra(Police.ENTITY_BODY, response);
    }
}
