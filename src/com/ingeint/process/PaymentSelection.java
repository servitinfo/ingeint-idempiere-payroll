package com.ingeint.process;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.eevolution.model.MHRMovement;
import org.eevolution.model.MHRPayroll;

import com.ingeint.base.CustomProcess;
import com.ingeint.model.MHRPaymentSelection;
import com.ingeint.model.MHRPaymentSelectionLine;
import com.ingeint.model.MPaymentSelectionType;

public class PaymentSelection extends CustomProcess {

	BigDecimal p_Percent = Env.ONE;

	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("Percent"))
				p_Percent = para[i].getParameterAsBigDecimal().divide(Env.ONEHUNDRED, 2, BigDecimal.ROUND_HALF_UP);
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
	}

	@Override
	protected String doIt() throws Exception {

		MHRPaymentSelection ps = new MHRPaymentSelection(getCtx(), getRecord_ID(), get_TrxName());
		MHRPayroll payroll = new MHRPayroll(null, ps.getHR_Process().getHR_Payroll_ID(), get_TrxName());
		Integer HR_Concept_ID = payroll.get_ValueAsInt("PaymentConcept_ID");

		if (HR_Concept_ID == null | HR_Concept_ID == 0) {

			HR_Concept_ID = ps.getING_PaymentSelectionType().getHR_Concept_ID();

		}

		MathContext mc = new MathContext(6); // 4 precision

		MHRMovement[] movement = null;
		if (ps.getC_BPartner_ID() > 0) {
			DB.executeUpdate(
					"DELETE FROM HR_PaymentSelectionLine WHERE HR_PaymentSelection_ID = ? AND C_BPartner_ID = ? ",
					new Object[] { ps.getHR_PaymentSelection_ID(), ps.getC_BPartner_ID() }, true, get_TrxName());
			movement = getmovement(ps.getHR_Process_ID(), HR_Concept_ID, ps.getC_BPartner_ID(), 0, 0, null);

		} else if (ps.getHR_Job_ID() > 0) {
			DB.executeUpdate("DELETE FROM HR_PaymentSelectionLine WHERE HR_PaymentSelection_ID = ? AND HR_Job_ID = ? ",
					new Object[] { ps.getHR_PaymentSelection_ID(), ps.getHR_Job_ID() }, true, get_TrxName());
			movement = getmovement(ps.getHR_Process_ID(), HR_Concept_ID, 0, ps.getHR_Job_ID(), 0, null);
		} else if (ps.getHR_Department_ID() > 0) {
			DB.executeUpdate(
					"DELETE FROM HR_PaymentSelectionLine WHERE HR_PaymentSelection_ID = ? AND HR_Department_ID = ? ",
					new Object[] { ps.getHR_PaymentSelection_ID(), ps.getHR_Department_ID() }, true, get_TrxName());
			movement = getmovement(ps.getHR_Process_ID(), HR_Concept_ID, 0, 0, ps.getHR_Department_ID(), "");
		} else if (ps.getEmployeeGroup() != null) {
			DB.executeUpdate(
					"DELETE FROM HR_PaymentSelectionLine WHERE HR_PaymentSelection_ID = ? AND EmployeeGroup = ? ",
					new Object[] { ps.getHR_PaymentSelection_ID(), ps.getEmployeeGroup() }, true, get_TrxName());
			movement = getmovement(ps.getHR_Process_ID(), HR_Concept_ID, 0, 0, 0, ps.getEmployeeGroup().toString());
		} else {
			DB.executeUpdate("DELETE FROM HR_PaymentSelectionLine WHERE HR_PaymentSelection_ID = ? ",
					new Object[] { ps.getHR_PaymentSelection_ID() }, true, get_TrxName());
			movement = getmovement(ps.getHR_Process_ID(), HR_Concept_ID, 0, 0, 0, null);
		}
		for (MHRMovement move : movement) {
			MHRPaymentSelectionLine psline = new MHRPaymentSelectionLine(ps);
			psline.setC_BPartner_ID(move.getC_BPartner_ID());
			psline.setAmount(move.getAmount());
			psline.setHR_Department_ID(move.getHR_Department_ID());
			psline.setHR_Job_ID(move.getHR_Job_ID());
			psline.setEmployeeGroup(ps.getEmployeeGroup());
			psline.setPayAmt(move.getAmount().multiply(p_Percent).round(mc));
			psline.setOpenAmt(move.getAmount().subtract(psline.getPayAmt()));
			psline.saveEx();
		}
		return null;
	}

	public MHRMovement[] getmovement(Integer HR_Process_ID, Integer HR_Concept_ID, Integer C_BPartner_ID,
			Integer HR_Job_ID, Integer HR_Department_ID, String EmployeeGroup) {

		StringBuilder whereClauseFinal = new StringBuilder();
		whereClauseFinal.append(MHRMovement.COLUMNNAME_HR_Process_ID + "=? ");
		whereClauseFinal.append("AND " + MHRMovement.COLUMNNAME_HR_Concept_ID + "=? ");

		Object[] params = new Object[] { HR_Process_ID, HR_Concept_ID };

		if (C_BPartner_ID > 0) {
			whereClauseFinal.append("AND C_BPartner_ID = ? ");
			params = new Object[] { HR_Process_ID, HR_Concept_ID, C_BPartner_ID };
		}
		if (HR_Job_ID > 0) {
			whereClauseFinal.append("AND HR_Job_ID = ? ");
			params = new Object[] { HR_Process_ID, HR_Concept_ID, HR_Job_ID };
		}

		if (HR_Department_ID > 0) {
			whereClauseFinal.append("AND HR_Department_ID = ? ");
			params = new Object[] { HR_Process_ID, HR_Concept_ID, HR_Department_ID };
		}

		if (EmployeeGroup != null) {
			whereClauseFinal.append("AND EmployeeGroup = ? ");
			params = new Object[] { HR_Process_ID, HR_Concept_ID, EmployeeGroup };
		}

		List<MHRMovement> list = new Query(getCtx(), MHRMovement.Table_Name, whereClauseFinal.toString(), get_TrxName())
				.setParameters(params).list();

		return list.toArray(new MHRMovement[list.size()]);
	}
}