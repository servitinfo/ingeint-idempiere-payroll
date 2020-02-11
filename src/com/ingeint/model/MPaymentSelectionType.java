package com.ingeint.model;

import java.sql.ResultSet;
import java.util.Properties;

public class MPaymentSelectionType extends X_ING_PaymentSelectionType {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7954314167051626831L;

	public MPaymentSelectionType(Properties ctx, int ING_PaymentSelectionType_ID, String trxName) {
		super(ctx, ING_PaymentSelectionType_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	public MPaymentSelectionType(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}

}
