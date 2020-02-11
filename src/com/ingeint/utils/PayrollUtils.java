package com.ingeint.utils;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MBPartner;
import org.compiere.model.MBankAccount;
import org.compiere.model.MPayment;
import org.compiere.util.DB;

import com.ingeint.model.MHRPaymentSelection;
import com.ingeint.model.MHRPaymentSelectionLine;;


public class PayrollUtils {
	
	public static MPayment createPayment(MHRPaymentSelectionLine psline) {		
		
		MHRPaymentSelection ps = new MHRPaymentSelection(psline.getCtx(), psline.getHR_PaymentSelection_ID(), psline.get_TrxName());
		MBankAccount ba = null;
		
		Integer C_BP_BankAccount_ID = DB.getSQLValue(psline.get_TrxName(), "SELECT C_BP_BankAccount_ID "
				+ "FROM C_BP_BankAccount "
				+ "WHERE C_BPartner_ID = ? AND C_Bank_ID = ? ", new Object[] {psline.getC_BPartner_ID(),psline.get_Value("C_BankAccountTo_ID")});
		
		MPayment payment = new MPayment(null, 0, psline.get_TrxName());
		MBPartner employee = new MBPartner(psline.getCtx(), psline.getC_BPartner_ID(), psline.get_TrxName());
		
		if (C_BP_BankAccount_ID>0) {
			ba = new MBankAccount(psline.getCtx(), C_BP_BankAccount_ID, psline.get_TrxName());
			payment.setAccountNo(ba.getAccountNo());
			payment.setC_BP_BankAccount_ID(C_BP_BankAccount_ID);
		}
		payment.set_ValueOfColumn("AD_Client_ID", psline.getAD_Client_ID());
		payment.setAD_Org_ID(psline.getAD_Org_ID());
		payment.setDateAcct(ps.getDateDoc());
		payment.setC_BPartner_ID(psline.get_ValueAsInt("C_BPartner_ID"));
		payment.setC_BankAccount_ID(ps.getC_BankAccount_ID());
		if (employee.get_Value("TenderType")==null)
			throw new AdempiereException("@FillTenderType@"+psline.getC_BPartner().getTaxID()+"_"+psline.getC_BPartner().getName());
		payment.setTenderType(employee.get_ValueAsString("TenderType"));
		payment.setDateTrx(payment.getDateAcct());
		payment.setC_DocType_ID(ps.getC_DocTypePayment_ID());
		payment.setDescription(psline.getDescription());
		payment.setC_Currency_ID(payment.getC_BankAccount().getC_Currency_ID());
		
		payment.setPayAmt(psline.getPayAmt());
		payment.setC_Charge_ID(ps.getC_Charge_ID());
		payment.setRoutingNo(psline.getHR_PaymentSelection().getRoutingNo());
		payment.saveEx();
		
		if (!employee.get_Value("TenderType").equals("K")) {
			payment.processIt("CO");
			payment.setProcessed(true);
			payment.saveEx();			
		}
		
		psline.setC_Payment_ID(payment.get_ID());
		psline.saveEx();
		
		return payment;
	}
}
