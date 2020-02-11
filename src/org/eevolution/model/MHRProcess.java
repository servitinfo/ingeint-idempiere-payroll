/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2007 e-Evolution,SC. All Rights Reserved.               *
 * Contributor(s): Victor Perez www.e-evolution.com                           *
 *****************************************************************************/
package org.eevolution.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MBPartner;
import org.compiere.model.MDocType;
import org.compiere.model.MFactAcct;
import org.compiere.model.MPeriod;
import org.compiere.model.MPeriodControl;
import org.compiere.model.MRule;
import org.compiere.model.MSysConfig;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.Query;
import org.compiere.model.Scriptlet;
import org.compiere.print.ReportEngine;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;

/**
 * HR Process Model
 * 
 * @author oscar.gomez@e-evolution.com, e-Evolution http://www.e-evolution.com
 *         <li>Original contributor of Payroll Functionality
 * @author victor.perez@e-evolution.com, e-Evolution http://www.e-evolution.com
 *         <li>FR [ 2520591 ] Support multiple calendar for Org
 * @see http 
 *      ://sourceforge.net/tracker2/?func=detail&atid=879335&aid=2520591&group_id
 *      =176962
 * @contributor Cristina Ghita, www.arhipac.ro
 * 
 * @contributor Jenny Rodriguez - jrodriguez@dcsla.com, Double Click Sistemas
 *              http://www.dcsla.com <li>
 * @contributor Rafael Salazar C. - rsalazar@dcsla.com, Double Click Sistemas
 *              http://www.dcsla.com <li>
 * 
 * @contributor Orlando Curieles - orlando.curieles@ingeint.com - INGEINT SA
 * 				https://www.ingeint.com
 */
public class MHRProcess extends X_HR_Process implements DocAction {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5310991830396703407L;

	public int m_C_BPartner_ID = 0;
	public int m_AD_User_ID = 0;
	public int m_HR_Concept_ID = 0;
	public String m_columnType = "";
	public Timestamp m_dateFrom;
	public Timestamp m_dateTo;
	/** HR_Concept_ID->MHRMovement */
	public Hashtable<Integer, MHRMovement> m_movement = new Hashtable<Integer, MHRMovement>();
	public MHRPayrollConcept[] linesConcept;
	/** The employee being processed */
	private MHREmployee m_employee;
	/** the context for rules */
	HashMap<String, Object> m_scriptCtx = new HashMap<String, Object>();
	/* stack of concepts executing rules - to check loop in recursion */
	private List<MHRConcept> activeConceptRule = new ArrayList<MHRConcept>();
	private MBPartner partner;

	/** Static Logger */
	private static CLogger s_log = CLogger.getCLogger(MHRProcess.class);
	public static final String CONCEPT_PP_COST_COLLECTOR_LABOR = "PP_COST_COLLECTOR_LABOR"; // HARDCODED
	Object m_description = null;
	boolean IsPayrollApplicableToEmployee = false;
	String DebugMode = MSysConfig.getValue("DEBUG_PAYROLL");

	private static StringBuilder s_scriptImport = new StringBuilder("")
			.append(" import org.compiere.model.*;")
			.append(" import org.adempiere.model.*;")
			.append(" import org.compiere.util.*;")
			.append(" import java.math.*;").append(" import java.sql.*;")
			.append(" import org.eevolution.model.*;");

	public static void addScriptImportPackage(String packageName) {
		s_scriptImport.append(" import ").append(packageName).append(";");
	}

	/**************************************************************************
	 * Default Constructor
	 * 
	 * @param ctx
	 *            context
	 * @param HR_Process_ID
	 *            To load, (0 create new order)
	 */
	public MHRProcess(Properties ctx, int HR_Process_ID, String trxName) {
		super(ctx, HR_Process_ID, trxName);
		if (HR_Process_ID == 0) {
			setDocStatus(DOCSTATUS_Drafted);
			setDocAction(DOCACTION_Prepare);
			setC_DocType_ID(0);
			set_ValueNoCheck("DocumentNo", null);
			setProcessed(false);
			setProcessing(false);
			setPosted(false);
			setHR_Department_ID(0);
			setC_BPartner_ID(0);
		}
	}

	/**
	 * Load Constructor
	 * 
	 * @param ctx
	 *            context
	 * @param rs
	 *            result set record
	 */
	public MHRProcess(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	} // MHRProcess

	@Override
	public final void setProcessed(boolean processed) {
		super.setProcessed(processed);
		if (get_ID() <= 0) {
			return;
		}
		final String sql = "UPDATE HR_Process SET Processed=? WHERE HR_Process_ID=?";
		DB.executeUpdateEx(sql, new Object[] { processed, get_ID() },
				get_TrxName());
	} // setProcessed

	@Override
	protected boolean beforeSave(boolean newRecord) {
		if (getAD_Client_ID() == 0) {
			throw new AdempiereException("@AD_Client_ID@ = 0");
		}
		if (getAD_Org_ID() == 0) {
			int context_AD_Org_ID = getAD_Org_ID();
			if (context_AD_Org_ID == 0) {
				throw new AdempiereException("@AD_Org_ID@ = *");
			}
			setAD_Org_ID(context_AD_Org_ID);
			log.warning("Changed Org to Context=" + context_AD_Org_ID);
		}
		setC_DocType_ID(getC_DocTypeTarget_ID());

		return true;
	}

	/**
	 * Process document
	 * 
	 * @param processAction
	 *            document action
	 * @return true if performed
	 */
	public boolean processIt(String processAction) {
		DocumentEngine engine = new DocumentEngine(this, getDocStatus());
		return engine.processIt(processAction, getDocAction());
	} // processIt

	/** Process Message */
	private String m_processMsg = null;
	/** Just Prepared Flag */
	private boolean m_justPrepared = false;

	/**
	 * Unlock Document.
	 * 
	 * @return true if success
	 */
	public boolean unlockIt() {
		log.info("unlockIt - " + toString());
		setProcessing(false);
		return true;
	} // unlockIt

	/**
	 * Invalidate Document
	 * 
	 * @return true if success
	 */
	public boolean invalidateIt() {
		log.info("invalidateIt - " + toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	} // invalidateIt

	/**************************************************************************
	 * Prepare Document
	 * 
	 * @return new status (In Progress or Invalid)
	 */
	public String prepareIt() {
		log.info("prepareIt - " + toString());

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null) {
			return DocAction.STATUS_Invalid;
		}

		// Std Period open?
		MHRPeriod period = MHRPeriod.get(getCtx(), getHR_Period_ID());
		MPeriod.testPeriodOpen(getCtx(),
				getHR_Period_ID() > 0 ? period.getDateAcct() : getDateAcct(),
						getC_DocTypeTarget_ID(), getAD_Org_ID());

		// New or in Progress/Invalid
		if (DOCSTATUS_Drafted.equals(getDocStatus())
				|| DOCSTATUS_InProgress.equals(getDocStatus())
				|| DOCSTATUS_Invalid.equals(getDocStatus())
				|| getC_DocType_ID() == 0) {
			setC_DocType_ID(getC_DocTypeTarget_ID());
		}

		try {
			createMovements();
		} catch (Exception e) {
			throw new AdempiereException(e);
		}

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		//
		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	} // prepareIt

	/**
	 * Complete Document
	 * 
	 * @return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	public String completeIt() {
		// Re-Check
		if (!m_justPrepared) {
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		// User Validation
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_AFTER_COMPLETE);
		if (m_processMsg != null) {
			return DocAction.STATUS_Invalid;
		}
		//
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	} // completeIt

	/**
	 * Approve Document
	 * 
	 * @return true if success
	 */
	public boolean approveIt() {
		return true;
	} // approveIt

	/**
	 * Reject Approval
	 * 
	 * @return true if success
	 */
	public boolean rejectIt() {
		log.info("rejectIt - " + toString());
		return true;
	} // rejectIt

	/**
	 * Post Document - nothing
	 * 
	 * @return true if success
	 */
	public boolean postIt() {
		log.info("postIt - " + toString());
		return false;
	} // postIt

	/**
	 * Void Document. Set Qtys to 0 - Sales: reverse all documents
	 * 
	 * @return true if success
	 */
	public boolean voidIt() {
		log.info("voidIt - " + toString());
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;

		// After Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_AFTER_VOID);
		if (m_processMsg != null)
			return false;

		setProcessed(true);
		setDocAction(DOCACTION_None);
		return true;
	} // voidIt

	/**
	 * Close Document. Cancel not delivered Quantities
	 * 
	 * @return true if success
	 */
	public boolean closeIt() {
		if (isProcessed()) {
			log.info(toString());

			// Before Close
			m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
					ModelValidator.TIMING_BEFORE_CLOSE);
			if (m_processMsg != null)
				return false;

			// After Close
			m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
					ModelValidator.TIMING_AFTER_CLOSE);
			if (m_processMsg != null)
				return false;

			setProcessed(true);
			setDocAction(DOCACTION_None);
			return true;
		}
		return false;
	} // closeIt

	/**
	 * Reverse Correction - same void
	 * 
	 * @return true if success
	 */
	public boolean reverseCorrectIt() {
		log.info("reverseCorrectIt - " + toString());
		// Before reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_BEFORE_REVERSECORRECT);
		if (m_processMsg != null)
			return false;

		// After reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_AFTER_REVERSECORRECT);
		if (m_processMsg != null)
			return false;

		return voidIt();
	} // reverseCorrectionIt

	/**
	 * Reverse Accrual - none
	 * 
	 * @return true if success
	 */
	public boolean reverseAccrualIt() {
		log.info("reverseAccrualIt - " + toString());
		// Before reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_BEFORE_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;

		// After reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_AFTER_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;

		return false;
	} // reverseAccrualIt

	/**
	 * Re-activate.
	 * 
	 * @return true if success
	 */
	public boolean reActivateIt() {
		log.info("reActivateIt - " + toString());

		// Before reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (m_processMsg != null)
			return false;

		// Can we delete posting
		MPeriod.testPeriodOpen(getCtx(), getDateAcct(),
				MPeriodControl.DOCBASETYPE_Payroll, getAD_Org_ID());

		// Delete
		StringBuilder sql = new StringBuilder(
				"DELETE FROM HR_Movement WHERE HR_Process_ID =").append(
						this.getHR_Process_ID()).append(" AND IsRegistered = 'N'");
		int no = DB.executeUpdate(sql.toString(), get_TrxName());
		log.fine("HR_Process deleted #" + no);

		// Delete Posting
		no = MFactAcct.deleteEx(MHRProcess.Table_ID, getHR_Process_ID(),
				get_TrxName());
		log.fine("Fact_Acct deleted #" + no);

		log.warning(DB.getSQLValueString(get_TrxName(), "DELETE "
				+ "FROM HR_Attribute "
				+ "WHERE HR_Process_ID = ? ", 
				getHR_Process_ID())+" Deleted Attributes");		

		// After reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,
				ModelValidator.TIMING_AFTER_REACTIVATE);
		if (m_processMsg != null)
			return false;

		setDocAction(DOCACTION_Complete);
		setProcessed(false);
		return true;
	} // reActivateIt

	/**
	 * Get Document Owner (Responsible)
	 * 
	 * @return AD_User_ID
	 */
	public int getDoc_User_ID() {
		return 0;
	} // getDoc_User_ID

	/**
	 * Get Document Approval Amount
	 * 
	 * @return amount
	 */
	public java.math.BigDecimal getApprovalAmt() {
		return BigDecimal.ZERO;
	} // getApprovalAmt

	/**
	 * 
	 */
	public int getC_Currency_ID() {
		return 0;
	}

	public String getProcessMsg() {
		return m_processMsg;
	}

	public String getSummary() {
		return "";
	}

	/**
	 * Create PDF
	 * 
	 * @return File or null
	 */
	public File createPDF() {
		try {
			File temp = File.createTempFile(get_TableName() + get_ID() + "_",
					".pdf");
			return createPDF(temp);
		} catch (Exception e) {
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	} // getPDF

	/**
	 * Create PDF file
	 * 
	 * @param file
	 *            output file
	 * @return file if success
	 */
	public File createPDF(File file) {
		ReportEngine re = ReportEngine.get(getCtx(), ReportEngine.ORDER, 0);
		if (re == null)
			return null;
		return re.getPDF(file);
	} // createPDF

	/**
	 * Get Document Info
	 * 
	 * @return document info (untranslated)
	 */
	public String getDocumentInfo() {
		org.compiere.model.MDocType dt = MDocType.get(getCtx(),
				getC_DocType_ID());
		return dt.getName() + " " + getDocumentNo();
	} // getDocumentInfo

	/**
	 * Get Lines
	 * 
	 * @param requery
	 *            requery
	 * @return lines
	 */
	public MHRMovement[] getLines(boolean requery) {
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// For HR_Process:
		whereClause.append(MHRMovement.COLUMNNAME_HR_Process_ID + "=?");
		params.add(getHR_Process_ID());
		// With Qty or Amounts
		whereClause.append(" AND (Qty <> 0 OR Amount <> 0)"); // TODO: it's
		// really needed
		// ?
		// Only Active Concepts
		whereClause
		.append(" AND EXISTS(SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Movement.HR_Concept_ID"
				+ " AND c.IsActive=?"
				+ " AND c.AccountSign NOT LIKE ?)"); // TODO : why ?
		// //red1
		// replace '<>'
		// with 'NOT
		// LIKE'
		params.add(true);
		params.add(MHRConcept.ACCOUNTSIGN_Natural); // TODO : why ?
		// Concepts with accounting
		whereClause
		.append(" AND EXISTS(SELECT 1 FROM HR_Concept_Acct ca WHERE ca.HR_Concept_ID=HR_Movement.HR_Concept_ID"
				+ " AND ca.IsActive=?)");
		params.add(true);
		// BPartner field is filled
		whereClause.append(" AND C_BPartner_ID IS NOT NULL");
		//
		// ORDER BY
		StringBuilder orderByClause = new StringBuilder();
		orderByClause
		.append("(SELECT bp.C_BP_Group_ID FROM C_BPartner bp WHERE bp.C_BPartner_ID=HR_Movement.C_BPartner_ID)");
		//
		List<MHRMovement> list = new Query(getCtx(), MHRMovement.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(orderByClause.toString()).list();
		return list.toArray(new MHRMovement[list.size()]);
	}

	/**
	 * Load HR_Movements and store them in a HR_Concept_ID->MHRMovement
	 * hashtable
	 * 
	 * @param movements
	 *            hashtable
	 * @param C_PBartner_ID
	 */
	private void loadMovements(Hashtable<Integer, MHRMovement> movements,
			int C_PBartner_ID) {
		final StringBuilder whereClause = new StringBuilder(
				MHRMovement.COLUMNNAME_HR_Process_ID).append("=? AND ")
				.append(MHRMovement.COLUMNNAME_C_BPartner_ID).append("=?");
		List<MHRMovement> list = new Query(getCtx(), MHRMovement.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(
						new Object[] { getHR_Process_ID(), C_PBartner_ID }).list();
		for (MHRMovement mvm : list) {
			if (movements.containsKey(mvm.getHR_Concept_ID())) {
				MHRMovement lastM = movements.get(mvm.getHR_Concept_ID());
				String columntype = lastM.getColumnType();
				if (columntype.equals(MHRConcept.COLUMNTYPE_Amount)) {
					mvm.addAmount(lastM.getAmount());
				} else if (columntype.equals(MHRConcept.COLUMNTYPE_Quantity)) {
					mvm.addQty(lastM.getQty());
				}
			}
			movements.put(mvm.getHR_Concept_ID(), mvm);
		}
	}

	/**
	 * Execute the script
	 * 
	 * @param AD_Rule_ID
	 * @param string
	 * @return
	 */
	private Object executeScript(int AD_Rule_ID, String columnType) {
		MRule rulee = MRule.get(getCtx(), AD_Rule_ID);
		Object result = null;
		m_description = null;
		String errorMsg = "";
		try {
			String text = "";
			if (rulee.getScript() != null) {
				text = rulee.getScript().trim()
						.replaceAll("\\bget", "process.get")
						.replace(".process.get", ".get");
			}
			if (columnType ==null)
				errorMsg = "El Tipo de columna no puede ser nulo "
						+ "para el Concepto que tiene la regla:"+rulee.getName();
			String resultType = "double result = 0;";
			if (MHRAttribute.COLUMNTYPE_Text.equals(columnType))
				resultType = "String result = null;";
			if (MHRAttribute.COLUMNTYPE_Date.equals(columnType)) {
				resultType = "Timestamp result = null;";
			}
			final String script = s_scriptImport.toString() + " " + resultType
					+ " String description = null;"
					+ " Timestamp serviceDate = null;" + text;
			Scriptlet engine = new Scriptlet(Scriptlet.VARIABLE, script,
					m_scriptCtx);
			Exception ex = engine.execute();
			m_description = engine.getDescription();
			if (m_description != null) {
				if (m_description.toString().length() >= "AdempiereException"
						.length())
					if (m_description.toString().contains("AdempiereException")) {
						errorMsg = m_description.toString();
						throw ex;
					}

			}
			if (ex != null) {

				throw ex;
			}
			result = engine.getResult(false);

		} catch (Exception e) {
			throw new AdempiereException("Execution error - @AD_Rule_ID@="
					+ rulee.getValue() + " \n " + errorMsg);
		}
		if (rulee.get_Value("ctxVariable")!=null)
			m_scriptCtx.put((String) rulee.get_Value("ctxVariable"), result);
		return result;
	}

	/**
	 * create movement for cost collector
	 * 
	 * @param C_BPartner_ID
	 * @param cc
	 * @return
	 */
	@SuppressWarnings("unused")
	private MHRMovement createMovementForCC(int C_BPartner_ID,
			I_PP_Cost_Collector cc) {
		// get the concept that should store the labor
		MHRConcept concept = MHRConcept.forValue(getCtx(),
				CONCEPT_PP_COST_COLLECTOR_LABOR);

		// get the attribute for specific concept
		List<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		whereClause
		.append("? >= ValidFrom AND ( ? <= ValidTo OR ValidTo IS NULL)");
		params.add(m_dateFrom);
		params.add(m_dateTo);
		whereClause.append(" AND HR_Concept_ID = ? ");
		params.add(concept.get_ID());
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept conc WHERE conc.HR_Concept_ID = HR_Attribute.HR_Concept_ID )");
		MHRAttribute att = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOnlyActiveRecords(true)
				.setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
				.first();
		if (att == null) {
			throw new AdempiereException(); // TODO ?? is necessary
		}

		if (MHRConcept.TYPE_RuleEngine.equals(concept.getType())) {
			Object result = null;

			m_scriptCtx.put("_CostCollector", cc);
			try {
				result = executeScript(att.getAD_Rule_ID(), att.getColumnType());
			} finally {
				m_scriptCtx.remove("_CostCollector");
			}
			if (result == null) {
				// TODO: throw exception ???
				log.warning("Variable (result) is null");
			}

			// get employee
			MHREmployee employee = MHREmployee.getActiveEmployee(getCtx(),
					C_BPartner_ID, getAD_Org_ID(), get_TrxName());

			// create movement
			MHRMovement mv = new MHRMovement(this, concept);
			mv.setC_BPartner_ID(C_BPartner_ID);
			mv.setAD_Rule_ID(att.getAD_Rule_ID());
			mv.setHR_Job_ID(employee.getHR_Job_ID());
			mv.setHR_Department_ID(employee.getHR_Department_ID());
			mv.setC_Activity_ID(employee.getC_Activity_ID());
			mv.setUser1_ID(employee.get_ValueAsInt("User1_ID"));
			mv.setValidFrom(m_dateFrom);
			mv.setValidTo(m_dateTo);
			mv.setPP_Cost_Collector_ID(cc.getPP_Cost_Collector_ID());
			mv.setIsRegistered(true);
			mv.setColumnValue(result);
			mv.setProcessed(true);
			mv.saveEx();
			return mv;
		} else {
			throw new AdempiereException(); // TODO ?? is necessary
		}

	}

	/**
	 * create Movements for corresponding process , period
	 */
	private void createMovements() throws Exception {
		// Valid if used definition of payroll per employee > ocurieles
		// 18Nov2014

		MHRPayroll Payroll = MHRPayroll.get(Env.getCtx(), getHR_Payroll_ID());
		boolean includeInActiveEmployee = Payroll.get_ValueAsBoolean("HR_IncludeInActiveEmployee");
		boolean allOrg = Payroll.get_ValueAsBoolean("HR_AllOrg");
		if (Payroll != null || !Payroll.equals(null))
			IsPayrollApplicableToEmployee = Payroll
			.get_ValueAsBoolean("IsemployeeApplicable");

		m_scriptCtx.clear();
		m_scriptCtx.put("process", this);
		m_scriptCtx.put("_Process", getHR_Process_ID());
		m_scriptCtx.put("_Period", getHR_Period_ID());
		m_scriptCtx.put("_Payroll", getHR_Payroll_ID());
		m_scriptCtx.put("_Department", getHR_Department_ID());

		log.info("info data - " + " Process: " + getHR_Process_ID()
		+ ", Period: " + getHR_Period_ID() + ", Payroll: "
		+ getHR_Payroll_ID() + ", Department: " + getHR_Department_ID());
		MHRPeriod period = new MHRPeriod(getCtx(), getHR_Period_ID(),
				get_TrxName());
		if (period != null) {
			m_dateFrom = period.getStartDate();
			m_dateTo = period.getEndDate();
			m_scriptCtx.put("_From", period.getStartDate());
			m_scriptCtx.put("_To", period.getEndDate());
		}

		// RE-Process, delete movement except concept type Incidence
		int no = DB
				.executeUpdateEx(
						"DELETE FROM HR_Movement m WHERE HR_Process_ID=? AND IsRegistered<>?",
						new Object[] { getHR_Process_ID(), true },
						get_TrxName());
		log.info("HR_Movement deleted #" + no);
		MBPartner[] linesEmployee =null;
		if (includeInActiveEmployee){
			linesEmployee = MHREmployee.getEmployeesAll(this,allOrg);
		}else{
			linesEmployee = MHREmployee.getEmployees(this);
		}
		linesConcept = MHRPayrollConcept.getPayrollConcepts(this);

		//
		int count = 1;
		for (MBPartner bp : linesEmployee) // ===============================================================
			// Employee
		{
			log.info("Employee " + count + "  ---------------------- "
					+ bp.getName());
			count++;
			m_C_BPartner_ID = bp.get_ID();
			if (DebugMode!=null && DebugMode.equals("Y"))
				log.warning("********Employee: **********"+bp.getName());
			if (getHR_Payroll_ID() > 0 && IsPayrollApplicableToEmployee && !allOrg){
				if (includeInActiveEmployee){
					m_employee = MHREmployee.getEmployee(getCtx(),
							m_C_BPartner_ID, getAD_Org_ID(), get_TrxName(),
							getHR_Payroll_ID());
				}else{
					m_employee = MHREmployee.getActiveEmployee(getCtx(),
							m_C_BPartner_ID, getAD_Org_ID(), get_TrxName(),
							getHR_Payroll_ID());
				}				
			}

			else{
				if (includeInActiveEmployee){
					m_employee = MHREmployee.getEmployee(getCtx(),
							m_C_BPartner_ID, get_TrxName());
				}else{
					m_employee = MHREmployee.getActiveEmployee(getCtx(),
							m_C_BPartner_ID, get_TrxName());	
				}

			}

			m_scriptCtx.remove("_DateStart");
			m_scriptCtx.remove("_DateEnd");
			m_scriptCtx.remove("_Days");
			m_scriptCtx.remove("_C_BPartner_ID");
			m_scriptCtx.put("_DateStart", m_employee.getStartDate());
			m_scriptCtx.put(
					"_DateEnd",
					m_employee.getEndDate() == null ? TimeUtil.getDay(2999, 12,
							31) : m_employee.getEndDate());
			m_scriptCtx.put(
					"_Days",
					org.compiere.util.TimeUtil.getDaysBetween(
							period.getStartDate(), period.getEndDate()) + 1);
			m_scriptCtx.put("_C_BPartner_ID", bp.getC_BPartner_ID());
			m_scriptCtx.put("_JobEmployee", m_employee.getHR_Job_ID());

			m_movement.clear();
			loadMovements(m_movement, m_C_BPartner_ID);
			//
			for (MHRPayrollConcept pc : linesConcept) // ====================================================
				// Concept
			{
				m_HR_Concept_ID = pc.getHR_Concept_ID();
				MHRConcept concept = MHRConcept.get(getCtx(), m_HR_Concept_ID);

				boolean printed = pc.isPrinted() || concept.isPrinted();
				Boolean byDate = concept.get_ValueAsBoolean("IsApplyByDate");

				if (byDate) {
					Timestamp[] dates = getDates2(period.getStartDate(), period.getEndDate());
					m_scriptCtx.put("_From", dates[0]);
					m_scriptCtx.put("_To", dates[1]);
				}

				MHRMovement movement = m_movement.get(concept.get_ID()); // as
				// it's
				// now
				// recursive,
				// it
				// can
				// happen
				// that
				// the
				// concept
				// is
				// already
				// generated
				if (movement == null) {

					if (DebugMode!=null && DebugMode.equals("Y"))
						log.warning("Debug concept: "+concept);
					movement = createMovementFromConcept(concept, printed);
					movement = m_movement.get(concept.get_ID());
					m_scriptCtx.put("_From", period.getStartDate());
					m_scriptCtx.put("_To", period.getEndDate());
				}
				if (movement == null) {
					continue;
					//throw new AdempiereException("Concept "
					//							+ concept.getValue() + " not created");
				}
				movement.set_ValueOfColumn("SeqNo", pc.getSeqNo());
			} // concept

			// Save movements:
			for (MHRMovement m : m_movement.values()) {
				MHRConcept c = (MHRConcept) m.getHR_Concept();
				if ((c.isRegistered() || m.isEmpty())
						&& !c.get_ValueAsBoolean("HR_MovementInsertForce")) {
					log.fine("Skip saving " + m);
				} else {
					boolean saveThisRecord = m.isPrinted() || c.isPaid()
							|| c.isPrinted();
					if (saveThisRecord)
						m.saveEx();
				}
			}
		} // for each employee
		//
		// Save period & finish
		period.setProcessed(true);
		period.saveEx();
	} // createMovements

	private MHRMovement createMovementFromConcept(MHRConcept concept,
			boolean printed) {
		log.info("Calculating concept " + concept.getValue());
		m_columnType = concept.getColumnType();

		List<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		whereClause
		.append("? >= ValidFrom AND ( ? <= ValidTo OR ValidTo IS NULL)");

		params.add(m_scriptCtx.get("_From"));
		params.add(m_scriptCtx.get("_To"));
		whereClause.append(" AND HR_Concept_ID = ? ");
		params.add(concept.getHR_Concept_ID());
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept conc WHERE conc.HR_Concept_ID = HR_Attribute.HR_Concept_ID )");

		// Check the concept is within a valid range for the attribute
		if (concept.isEmployee()) {
			whereClause
			.append(" AND C_BPartner_ID = ? AND (HR_Employee_ID = ? OR HR_Employee_ID IS NULL)");
			params.add(m_employee.getC_BPartner_ID());
			params.add(m_employee.get_ID());
		}

		MHRAttribute att = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOnlyActiveRecords(true)
				.setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
				.first();
		if (att == null || concept.isRegistered()) {
			log.info("Skip concept " + concept + " - attribute not found");
			MHRMovement dummymov = new MHRMovement(getCtx(), 0, get_TrxName());
			dummymov.setIsRegistered(true); // to avoid landing on movement
			// table
			m_movement.put(concept.getHR_Concept_ID(), dummymov);
			return dummymov;
		}

		log.info("Concept - " + concept.getName());
		MHRMovement movement = new MHRMovement(getCtx(), 0, get_TrxName());

		partner = new MBPartner(getCtx(), m_employee.getC_BPartner_ID(), get_TrxName());

		movement.setC_BPartner_ID(m_C_BPartner_ID);
		movement.setHR_Concept_ID(concept.getHR_Concept_ID());
		movement.setHR_Concept_Category_ID(concept.getHR_Concept_Category_ID());
		movement.setHR_Process_ID(getHR_Process_ID());
		movement.setHR_Department_ID(m_employee.getHR_Department_ID());
		movement.setHR_Job_ID(m_employee.getHR_Job_ID());
		movement.setColumnType(m_columnType);
		movement.setAD_Rule_ID(att.getAD_Rule_ID());
		movement.setValidFrom((Timestamp) m_scriptCtx.get("_From"));
		movement.setValidTo((Timestamp) m_scriptCtx.get("_To"));
		movement.setIsPrinted(printed);
		movement.setIsRegistered(concept.isRegistered());
		movement.setC_Activity_ID(m_employee.getC_Activity_ID());
		movement.setUser1_ID(m_employee.get_ValueAsInt("User1_ID"));
		movement.set_ValueOfColumn("EmployeeGroup", partner.get_Value("EmployeeGroup"));
		if (MHRConcept.TYPE_RuleEngine.equals(concept.getType())) {
			log.info("Executing rule for concept " + concept.getValue());
			movement.setAccountSign(concept.getAccountSign());
			if (activeConceptRule.contains(concept)) {
				throw new AdempiereException(
						"Recursion loop detected in concept "
								+ concept.getValue());
			}
			activeConceptRule.add(concept);
			Object result = executeScript(att.getAD_Rule_ID(),
					att.getColumnType());
			activeConceptRule.remove(concept);
			if (result == null) {
				// TODO: throw exception ???
				log.warning("Variable (result) is null");
				return movement;
			}
			movement.setColumnValue(result); // double rounded in
			// MHRMovement.setColumnValue
			if (m_description != null)
				movement.setDescription(m_description.toString());
		} else {
			movement.setQty(att.getQty());
			movement.setAmount(att.getAmount());
			movement.setTextMsg(att.getTextMsg());
			movement.setServiceDate(att.getServiceDate());
		}
		movement.setProcessed(true);
		movement.setAD_Org_ID(getAD_Org_ID());
		m_movement.put(concept.getHR_Concept_ID(), movement);
		//movement.saveEx();
		return movement;
	}

	// Helper methods
	// -------------------------------------------------------------------------------

	/**
	 * Helper Method : get the value of the concept
	 * 
	 * @param pconcept
	 * @return
	 */
	public double getConcept(String pconcept) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pconcept.trim());

		if (concept == null) { // red1 - return 0;
			throw new AdempiereException(
					pconcept
					+ " does not exist. Please create it first in Payroll Concept");
		}

		MHRMovement m = m_movement.get(concept.get_ID());
		if (m == null) {
			createMovementFromConcept(concept, concept.isPrinted());
			m = m_movement.get(concept.get_ID());
		}
		if (m == null) {
			throw new AdempiereException("Concept " + concept.getValue()
			+ " not created");
		}

		String type = m.getColumnType();
		if (MHRMovement.COLUMNTYPE_Amount.equals(type)) {
			return m.getAmount().doubleValue();
		} else if (MHRMovement.COLUMNTYPE_Quantity.equals(type)) {
			return m.getQty().doubleValue();
		} else {
			// TODO: throw exception ?
			return 0;
		}
	} // getConcept

	/**
	 * Helper Method : get the value of the concept string type
	 * 
	 * @param pconcept
	 * @return
	 */
	public String getConceptString(String pconcept) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pconcept.trim());

		if (concept == null) {
			return null; // TODO throw exception ?
		}

		MHRMovement m = m_movement.get(concept.get_ID());
		if (m == null) {
			createMovementFromConcept(concept, concept.isPrinted());
			m = m_movement.get(concept.get_ID());
		}

		String type = m.getColumnType();
		if (MHRMovement.COLUMNTYPE_Text.equals(type)) {
			return m.getTextMsg();
		} else {
			// TODO: throw exception ?
			return null;
		}
	} // getConceptString

	/**
	 * Helper Method : get the value of the concept date type
	 * 
	 * @param pconcept
	 * @return
	 */
	public Timestamp getConceptDate(String pconcept) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pconcept.trim());

		if (concept == null) {
			return null; // TODO throw exception ?
		}

		MHRMovement m = m_movement.get(concept.get_ID());
		if (m == null) {
			createMovementFromConcept(concept, concept.isPrinted());
			m = m_movement.get(concept.get_ID());
		}
		if (m==null){
			return null;
		}
		String type = m.getColumnType();
		if (MHRMovement.COLUMNTYPE_Text.equals(type)) {
			return m.getServiceDate();
		}else if (MHRMovement.COLUMNTYPE_Date.equals(type)){
			return m.getServiceDate();
		}else {
			// TODO: throw exception ?
			return null;
		}
	} // getConceptDate

	/**
	 * Helper Method : sets the value of a concept
	 * 
	 * @param conceptValue
	 * @param value
	 */
	public void setConcept(String conceptValue, double value) {
		try {
			MHRConcept c = MHRConcept.forValue(getCtx(), conceptValue);
			if (c == null) {
				return; // TODO throw exception
			}
			MHRMovement m = new MHRMovement(getCtx(), 0, get_TrxName());
			MHREmployee employee = MHREmployee.getActiveEmployee(getCtx(),
					m_C_BPartner_ID, getAD_Org_ID(), get_TrxName());
			m.setColumnType(c.getColumnType());
			m.setColumnValue(BigDecimal.valueOf(value));

			m.setHR_Process_ID(getHR_Process_ID());
			m.setHR_Concept_ID(m_HR_Concept_ID);
			m.setC_BPartner_ID(m_C_BPartner_ID);
			m.setDescription("Added From Rule"); // TODO: translate
			m.setValidFrom(m_dateTo);
			m.setValidTo(m_dateTo);

			m.setHR_Concept_Category_ID(c.getHR_Concept_Category_ID());
			m.setHR_Department_ID(employee.getHR_Department_ID());
			m.setHR_Job_ID(employee.getHR_Job_ID());
			m.setIsRegistered(c.isRegistered());
			m.setC_Activity_ID(employee.getC_Activity_ID());
			m.setUser1_ID(employee.get_ValueAsInt("User1_ID"));
			// m.setProcessed(true); ??

			m.saveEx();
		} catch (Exception e) {
			s_log.warning(e.getMessage());
		}
	} // setConcept

	/*
	 * Helper Method : sets the value of a concept and set if isRegistered
	 * 
	 * @param conceptValue
	 * 
	 * @param value
	 * 
	 * @param isRegistered
	 */
	public void setConcept(String conceptValue, double value,
			boolean isRegistered) {
		try {
			MHRConcept c = MHRConcept.forValue(getCtx(), conceptValue);
			if (c == null) {
				return; // TODO throw exception
			}
			MHRMovement m = new MHRMovement(Env.getCtx(), 0, get_TrxName());
			MHREmployee employee = MHREmployee.getActiveEmployee(getCtx(),
					m_C_BPartner_ID, getAD_Org_ID(), get_TrxName());
			m.setColumnType(c.getColumnType());
			if (c.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount))
				m.setAmount(BigDecimal.valueOf(value));
			else if (c.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
				m.setQty(BigDecimal.valueOf(value));
			else
				return;
			m.setHR_Process_ID(getHR_Process_ID());
			m.setHR_Concept_ID(c.getHR_Concept_ID());
			m.setC_BPartner_ID(m_C_BPartner_ID);
			m.setDescription("Added From Rule"); // TODO: translate
			m.setValidFrom(m_dateTo);
			m.setValidTo(m_dateTo);
			m.setIsRegistered(isRegistered);

			m.setHR_Concept_Category_ID(c.getHR_Concept_Category_ID());
			m.setHR_Department_ID(employee.getHR_Department_ID());
			m.setHR_Job_ID(employee.getHR_Job_ID());
			m.setIsRegistered(c.isRegistered());
			m.setC_Activity_ID(employee.getC_Activity_ID());
			m.setUser1_ID(employee.get_ValueAsInt("User1_ID"));
			// m.setProcessed(true); ??

			m.saveEx();
		} catch (Exception e) {
			s_log.warning(e.getMessage());
		}
	} // setConcept

	/**
	 * Helper Method : get the sum of the concept values, grouped by the
	 * Category
	 * 
	 * @param pconcept
	 * @return
	 */
	public double getConceptGroup(String pconcept) {
		final MHRConceptCategory category = MHRConceptCategory.forValue(
				getCtx(), pconcept);
		if (category == null) {
			return 0.0; // TODO: need to throw exception ?
		}
		//
		double value = 0.0;
		for (MHRPayrollConcept pc : linesConcept) {
			MHRConcept con = MHRConcept.get(getCtx(), pc.getHR_Concept_ID());
			if (con.getHR_Concept_Category_ID() == category.get_ID()) {
				MHRMovement movement = m_movement.get(pc.getHR_Concept_ID());
				if (movement == null) {
					createMovementFromConcept(con, con.isPrinted());
					movement = m_movement.get(con.get_ID());
				} else {
					String columnType = movement.getColumnType();
					if (MHRConcept.COLUMNTYPE_Amount.equals(columnType)) {
						value += movement.getAmount().doubleValue();
					} else if (MHRConcept.COLUMNTYPE_Quantity
							.equals(columnType)) {
						value += movement.getQty().doubleValue();
					}
				}
			}
		}
		return value;
	} // getConceptGroup

	/**
	 * Helper Method : Get Concept [get concept to search key ]
	 * 
	 * @param pList
	 *            Value List
	 * @param amount
	 *            Amount to search
	 * @param column
	 *            Number of column to return (1.......8)
	 * @return The amount corresponding to the designated column 'column'
	 */
	public double getList(String pList, double amount, String columnParam) {
		BigDecimal value = Env.ZERO;
		String column = columnParam;
		if (m_columnType.equals(MHRConcept.COLUMNTYPE_Amount)) {
			column = column.toString().length() == 1 ? "Col_" + column
					: "Amount" + column;
			ArrayList<Object> params = new ArrayList<Object>();
			String sqlList = "SELECT "
					+ column
					+ " FROM HR_List l "
					+ "INNER JOIN HR_ListVersion lv ON (lv.HR_List_ID=l.HR_List_ID) "
					+ "INNER JOIN HR_ListLine ll ON (ll.HR_ListVersion_ID=lv.HR_ListVersion_ID) "
					+ "WHERE l.IsActive='Y' AND lv.IsActive='Y' AND ll.IsActive='Y' AND l.Value = ? AND "
					+ "l.AD_Client_ID = ? AND "
					+ "(? BETWEEN lv.ValidFrom AND lv.ValidTo ) AND "
					+ "(? BETWEEN ll.MinValue AND	ll.MaxValue)";
			params.add(pList);
			params.add(getAD_Client_ID());
			params.add(m_dateFrom);
			params.add(BigDecimal.valueOf(amount));

			value = DB.getSQLValueBDEx(get_TrxName(), sqlList, params);
		}
		//
		if (value == null) {
			throw new IllegalStateException("getList Out of Range");
		}
		return value.doubleValue();
	} // getList

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @return Amount of concept, applying to employee
	 */
	public double getAttribute(String pConcept) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");

		params.add(m_dateFrom);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' "
				+ " AND c.Value = ?)");
		params.add(pConcept);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0.0;

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
			return attribute.getQty().doubleValue();

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount))
			return attribute.getAmount().doubleValue();

		// something else
		return 0.0; // TODO throw exception ??
	} // getAttribute

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @return Max Value of concept, applying to employee
	 */

	public double getAttributeMax(String pConcept, Timestamp date1,
			Timestamp date2) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;
		ArrayList<Object> params = new ArrayList<Object>();
		Timestamp [] dates = null;

		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(date2);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' AND c.Value = ? "
				+ " AND (HR_Attribute.validto IS NULL OR HR_Attribute.validto >= ?) )");
		params.add(pConcept);
		params.add(date1);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(getC_BPartner_ID());
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0.0;

		BigDecimal bd = (BigDecimal) attribute
				.get_Value(I_HR_Attribute.COLUMNNAME_MaxValue);
		if (bd == null)
			return 0.0;
		return bd.doubleValue();

	} // getAttribute MAX

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @return Min Value of concept, applying to employee
	 */

	public double getAttributeMin(String pConcept, Timestamp date1,
			Timestamp date2) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(date2);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' AND c.Value = ? "
				+ " AND (HR_Attribute.validto IS NULL OR HR_Attribute.validto >= ?) )");
		params.add(pConcept);
		params.add(date1);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(getC_BPartner_ID());
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0.0;

		BigDecimal bd = (BigDecimal) attribute
				.get_Value(I_HR_Attribute.COLUMNNAME_MinValue);
		if (bd == null)
			return 0.0;
		return bd.doubleValue();

	} // getAttribute Min
	// LVE Localizacio n Venezuela - RTSC: 14/03/2011

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept and
	 * date ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @param date
	 * @return Amount of concept, applying to employee
	 */
	public double getAttribute(String pConcept, Timestamp date) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;
		ArrayList<Object> params = new ArrayList<Object>();

		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append(" AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID  AND HR_Attribute.IsActive='Y' AND c.Value = ? AND ((? >= HR_Attribute.validfrom AND HR_Attribute.validto IS NULL) OR (? >= HR_Attribute.validfrom AND ? <= HR_Attribute.validto)))");
		params.add(pConcept);
		params.add(date);
		params.add(date);
		params.add(date);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0.0;

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
			return attribute.getQty().doubleValue();

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount))
			return attribute.getAmount().doubleValue();

		// something else
		return 0.0; // TODO throw exception ??
	} // getAttribute

	// LVE Localizacion Venezuela - JCRA: 14/03/2011
	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept and
	 * date ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @param date1
	 * @param date2
	 * @return Amount of concept, applying to employee
	 */
	public double getAttribute(String pConcept, Timestamp date1, Timestamp date2) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(date2);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' AND c.Value = ? "
				+ " AND (HR_Attribute.validto IS NULL OR HR_Attribute.validto >= ?) )");
		params.add(pConcept);
		params.add(date1);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute
		// if (concept.isEmployee()){
		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());
		// }

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				// .setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0.0;

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
			return attribute.getQty().doubleValue();

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount))
			return attribute.getAmount().doubleValue();

		// something else
		return 0.0; // TODO throw exception ??
	} // getAttribute

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept and
	 * date ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @param date1
	 * @param date2
	 * @return Amount of concept, applying to employee
	 */
	public double getAttribute(String pConcept, Timestamp date1,
			Timestamp date2, int pJob) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(date2);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' AND c.Value = ? "
				+ " AND (HR_Attribute.validto IS NULL OR HR_Attribute.validto >= ?) )");
		params.add(pConcept);
		params.add(date1);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute
		// if (concept.isEmployee()){
		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());
		// }

		if (pJob != 0) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_HR_Job_ID
					+ " = ? ");
			params.add(pJob);
		}
		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				// .setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0.0;

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
			return attribute.getQty().doubleValue();

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount))
			return attribute.getAmount().doubleValue();

		// something else
		return 0.0; // TODO throw exception ??
	} // getAttribute

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept and
	 * date ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @param date1
	 * @param date2
	 * @return SUM(Amount) of concept, applying to employee Freddy Heredia
	 *         12/12/2014
	 */
	public double getAttributeSUM(String pConcept, Timestamp date1,
			Timestamp date2) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");

		params.add(date2);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' AND c.Value = ? "
				+ " AND (HR_Attribute.validto IS NULL OR HR_Attribute.validto >= ?) )");
		params.add(pConcept);
		params.add(date1);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute
		// if (concept.isEmployee()){
		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());
		// }

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity)) {
			BigDecimal quantity = new Query(getCtx(), MHRAttribute.Table_Name,
					whereClause.toString(), get_TrxName())
					.setParameters(params)
					.setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
					.sum("Qty");

			if (quantity == null)
				return 0.0;
			else
				return quantity.doubleValue();
		}

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount)) {
			BigDecimal amount = new Query(getCtx(), MHRAttribute.Table_Name,
					whereClause.toString(), get_TrxName())
					.setParameters(params)
					.setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
					.sum("Amount");

			if (amount == null)
				return 0.0;
			else
				return amount.doubleValue();
		}

		// something else
		return 0.0; // TODO throw exception ??
	} // getAttribute

	public double getAttribute(String pConcept, String pDescription) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(m_dateFrom);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' "
				+ " AND c.Value = ? AND HR_Attribute.Description = ?)");
		params.add(pConcept);
		params.add(pDescription);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(getC_BPartner_ID());
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0.0;

		// if column type is Quantity return quantity
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Quantity))
			return attribute.getQty().doubleValue();

		// if column type is Amount return amount
		if (concept.getColumnType().equals(MHRConcept.COLUMNTYPE_Amount))
			return attribute.getAmount().doubleValue();

		// something else
		return 0.0; // TODO throw exception ??
	} // getAttribute

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param conceptValue
	 * @return ServiceDate
	 */
	public Timestamp getAttributeDate(String conceptValue, Timestamp date) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return null;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND c.Value = ?"
				+ " AND ((? >= HR_Attribute.validfrom AND HR_Attribute.validto IS NULL) OR (? >= HR_Attribute.validfrom AND ? "
				+ "<= HR_Attribute.validto)))");
		params.add(conceptValue);
		params.add(date);
		params.add(date);
		params.add(date);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return null;

		return attribute.getServiceDate();
	} // getAttributeDate

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param conceptValue
	 * @Region pRegion
	 * @return ServiceDate
	 */

	public double getAttributeOfPeriod(String conceptValue, Timestamp From, Timestamp To) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return 0;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND c.Value = ?"
				+ " AND HR_Attribute.isActive = 'Y')");
		params.add(conceptValue);

		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return 0;
		
		if (attribute.getColumnType().equals(MHRAttribute.COLUMNTYPE_Quantity)) {
			
			if (attribute.getValidFrom().compareTo(attribute.getValidTo())==0) {
				return 1;
			
			}else if (attribute.getValidTo().compareTo(To)>0 
					|| attribute.getValidTo().compareTo(To)==0) {

				Date xFrom = new Date (attribute.getValidFrom().getTime());
				Date xToPeriod = new Date (To.getTime());				
				Integer days = DaysTotal(xToPeriod,xFrom);
							
				return days;

			}else if (attribute.getValidTo().compareTo(From)>0 
					|| (attribute.getValidTo().compareTo(From)==0)) {				
				Date xTo = new Date (attribute.getValidTo().getTime());
				Date xFromPeriod = new Date (From.getTime());
				Integer days = DaysTotal(xTo, xFromPeriod);

				return days;				
			}
		}
		return 0;
	}

	public Timestamp getAttributeDate(String conceptValue, String pRegion) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return null;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND c.Value = ? "
				+ "AND HR_Region = ?)");
		params.add(conceptValue);
		params.add(pRegion);

		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return null;

		return attribute.getServiceDate();
	} // getAttributeDate

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param conceptValue
	 * @return ServiceDate
	 */
	public Timestamp getAttributeDate(String conceptValue) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return null;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID"
				+ " AND c.Value = ?)");
		params.add(conceptValue);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizaci��n Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return null;

		return attribute.getServiceDate();
	} // getAttributeDate

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param conceptValue
	 * @return TextMsg
	 */
	public String getAttributeString(String conceptValue) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return null;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID"
				+ " AND c.Value = ?)");
		params.add(conceptValue);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizaci��n Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null)
			return null;

		return attribute.getTextMsg();
	} // getAttributeString

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept ]
	 * 
	 * @param conceptValue
	 * @return TextMsg
	 */
	public String getAttributeString(String conceptValue, Timestamp from,
			Timestamp to) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return null;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept

		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' AND c.Value = ? "
				+ " AND (HR_Attribute.validto IS NULL OR HR_Attribute.validto >= ?) AND HR_Attribute.ValidFrom <=?"
				+ " )");
		params.add(conceptValue);
		params.add(from);
		params.add(to);
		//

		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizaci��n Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute == null) {
			// System.out.println(whereClause.toString()+",Parmeter"+params.toString());
			return null;
		}
		return attribute.getTextMsg();
	} // getAttributeString


	/**
	 * Helper Method : Get the number of days between start and end, in
	 * Timestamp format
	 * 
	 * @param date1
	 * @param date2
	 * @return no. of days
	 */
	public int getDays(Timestamp date1, Timestamp date2) {
		// adds one for the last day
		return org.compiere.util.TimeUtil.getDaysBetween(date1, date2) + 1;
	} // getDays

	/**
	 * Helper Method : Get the number of days between start and end, in String
	 * format
	 * 
	 * @param date1
	 * @param date2
	 * @return no. of days
	 */
	public int getDays(String date1, String date2) {
		Timestamp dat1 = Timestamp.valueOf(date1);
		Timestamp dat2 = Timestamp.valueOf(date2);
		return getDays(dat1, dat2);
	} // getDays

	/**
	 * Helper Method : Get Months, Date in Format Timestamp
	 * 
	 * @param start
	 * @param end
	 * @return no. of month between two dates
	 */
	public int getMonths(Timestamp startParam, Timestamp endParam) {
		boolean negative = false;
		Timestamp start = startParam;
		Timestamp end = endParam;
		if (end.before(start)) {
			negative = true;
			Timestamp temp = start;
			start = end;
			end = temp;
		}

		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(start);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		GregorianCalendar calEnd = new GregorianCalendar();

		calEnd.setTime(end);
		calEnd.set(Calendar.HOUR_OF_DAY, 0);
		calEnd.set(Calendar.MINUTE, 0);
		calEnd.set(Calendar.SECOND, 0);
		calEnd.set(Calendar.MILLISECOND, 0);

		if (cal.get(Calendar.YEAR) == calEnd.get(Calendar.YEAR)) {
			if (negative)
				return (calEnd.get(Calendar.MONTH) - cal.get(Calendar.MONTH))
						* -1;
			return calEnd.get(Calendar.MONTH) - cal.get(Calendar.MONTH);
		}

		// not very efficient, but correct
		int counter = 0;
		while (calEnd.after(cal)) {
			cal.add(Calendar.MONTH, 1);
			counter++;
		}
		if (negative)
			return counter * -1;
		return counter;
	} // getMonths

	/**
	 * Helper Method : Concept for a range from-to in periods. Periods with
	 * values of 0 -1 1, etc. actual previous one period, next period 0
	 * corresponds to actual period.
	 * 
	 * @param conceptValue
	 *            concept key(value)
	 * @param periodFrom
	 *            the search is done by the period value, it helps to search
	 *            from previous years
	 * @param periodTo
	 */
	public double getConcept(String conceptValue, int periodFrom, int periodTo) {
		return getConcept(conceptValue, null, periodFrom, periodTo);
	} // getConcept

	/**
	 * Helper Method : Concept by range from-to in periods from a different
	 * payroll periods with values 0 -1 1, etc. actual previous one period, next
	 * period 0 corresponds to actual period
	 * 
	 * @param conceptValue
	 * @param pFrom
	 * @param pTo
	 *            the search is done by the period value, it helps to search
	 *            from previous years
	 * @param payrollValue
	 *            is the value of the payroll.
	 */
	public double getConcept(String conceptValue, String payrollValue,
			int periodFrom, int periodTo) {
		int payroll_id;
		if (payrollValue == null) {
			payroll_id = getHR_Payroll_ID();
		} else {
			payroll_id = MHRPayroll.forValue(getCtx(), payrollValue).get_ID();
		}

		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return 0.0;
		//
		// Detect field name
		final String fieldName;
		if (MHRConcept.COLUMNTYPE_Quantity.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Qty;
		} else if (MHRConcept.COLUMNTYPE_Amount.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Amount;
		} else {
			return 0; // TODO: throw exception?
		}
		//
		MHRPeriod p = MHRPeriod.get(getCtx(), getHR_Period_ID());
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_HR_Concept_ID
				+ "=?");
		params.add(concept.get_ID());
		// check partner
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_C_BPartner_ID
				+ "=?");
		params.add(m_C_BPartner_ID);
		//
		// check process and payroll
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Process p"
				+ " INNER JOIN HR_Period pr ON (pr.HR_Period_id=p.HR_Period_ID)"
				+ " WHERE HR_Movement.HR_Process_ID = p.HR_Process_ID"
				+ " AND p.HR_Payroll_ID=?");

		params.add(payroll_id);
		if (periodFrom < 0) {
			whereClause.append(" AND pr.PeriodNo >= ?");
			params.add(p.getPeriodNo() + periodFrom);
		}
		if (periodTo > 0) {
			whereClause.append(" AND pr.PeriodNo <= ?");
			params.add(p.getPeriodNo() + periodTo);
		}
		whereClause.append(")");
		//
		StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(")
				.append(fieldName).append("),0) FROM ")
				.append(MHRMovement.Table_Name).append(" WHERE ")
				.append(whereClause);
		BigDecimal value = DB.getSQLValueBDEx(get_TrxName(), sql.toString(),
				params);
		return value.doubleValue();

	} // getConcept

	/**
	 * Helper Method: gets Concept value of a payrroll between 2 dates
	 * 
	 * @param pConcept
	 * @param pPayrroll
	 * @param from
	 * @param to
	 * */
	public double getConcept(String conceptValue, String payrollValue,
			Timestamp from, Timestamp to) {
		int payroll_id;
		if (payrollValue == null) {
			payroll_id = getHR_Payroll_ID();
		} else {
			payroll_id = MHRPayroll.forValue(getCtx(), payrollValue).get_ID();
		}

		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);
		if (concept == null)
			return 0.0;
		//
		// Detect field name
		final String fieldName;
		if (MHRConcept.COLUMNTYPE_Quantity.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Qty;
		} else if (MHRConcept.COLUMNTYPE_Amount.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Amount;
		} else {
			return 0; // TODO: throw exception?
		}
		//
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_HR_Concept_ID
				+ "=?");
		params.add(concept.get_ID());
		// check partner
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_C_BPartner_ID
				+ "=?");
		params.add(m_C_BPartner_ID);
		// Adding dates
		whereClause.append(" AND validTo BETWEEN ? AND ?");
		params.add(from);
		params.add(to);
		//
		// check process and payroll
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Process p"
				+ " INNER JOIN HR_Period pr ON (pr.HR_Period_id=p.HR_Period_ID)"
				+ " WHERE HR_Movement.HR_Process_ID = p.HR_Process_ID"
				+ " AND p.HR_Payroll_ID=?");

		params.add(payroll_id);

		whereClause.append(")");
		//
		StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(")
				.append(fieldName).append("),0) FROM ")
				.append(MHRMovement.Table_Name).append(" WHERE ")
				.append(whereClause);
		BigDecimal value = DB.getSQLValueBDEx(get_TrxName(), sql.toString(),
				params);
		return value.doubleValue();

	} // getConcept

	/**
	 * TODO QSS Reviewme Helper Method: gets Concept value of payrroll(s)
	 * between 2 dates if payrollValue is null then sum all payrolls between 2
	 * dates if dates range are null then set them based on first and last day
	 * of period
	 * 
	 * @param pConcept
	 * @param from
	 * @param to
	 * */
	public double getConceptRangeOfPeriod(String conceptValue,
			String payrollValue, String dateFrom, String dateTo) {
		int payroll_id = -1;
		if (payrollValue == null) {
			// payroll_id = getHR_Payroll_ID();
			payroll_id = 0; // all payrrolls
		} else {
			payroll_id = MHRPayroll.forValue(getCtx(), payrollValue).get_ID();
		}
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);

		if (concept == null)
			return 0.0;

		Timestamp from = null;
		Timestamp to = null;

		if (dateFrom != null)
			from = Timestamp.valueOf(dateFrom);
		if (dateTo != null)
			to = Timestamp.valueOf(dateTo);

		// Detect field name
		final String fieldName;
		if (MHRConcept.COLUMNTYPE_Quantity.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Qty;
		} else if (MHRConcept.COLUMNTYPE_Amount.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Amount;
		} else {
			return 0; // TODO: throw exception?
		}
		//
		MHRPeriod p = MHRPeriod.get(getCtx(), getHR_Period_ID());
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_HR_Concept_ID
				+ "=?");
		params.add(concept.get_ID());
		// check partner
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_C_BPartner_ID
				+ "=?");
		params.add(getM_C_BPartner_ID());
		// Adding Organization
		whereClause.append(" AND ( " + MHRMovement.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRMovement.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());
		// Adding dates
		whereClause.append(" AND validTo BETWEEN ? AND ?");
		if (from == null)
			from = getFirstDayOfPeriod(p.getHR_Period_ID());
		if (to == null)
			to = getLastDayOfPeriod(p.getHR_Period_ID());
		params.add(from);
		params.add(to);
		//
		// check process and payroll
		if (payroll_id > 0) {
			whereClause
			.append(" AND EXISTS (SELECT 1 FROM HR_Process p"
					+ " INNER JOIN HR_Period pr ON (pr.HR_Period_id=p.HR_Period_ID)"
					+ " WHERE HR_Movement.HR_Process_ID = p.HR_Process_ID"
					+ " AND p.HR_Payroll_ID=?");

			params.add(payroll_id);
			whereClause.append(")");
			//
		}
		StringBuffer sql = new StringBuffer("SELECT COALESCE(SUM(")
				.append(fieldName).append("),0) FROM ")
				.append(MHRMovement.Table_Name).append(" WHERE ")
				.append(whereClause);
		BigDecimal value = DB.getSQLValueBDEx(get_TrxName(), sql.toString(),
				params);
		if (value != null)
			return value.doubleValue();
		else
			return 0;

	} // getConceptRangeOfPeriod
	
	/**
	 * TODO QSS Reviewme Helper Method: gets Concept value of payrroll(s)
	 * between 2 dates if payrollValue is null then sum all payrolls between 2
	 * dates if dates range are null then set them based on first and last day
	 * of period
	 * 
	 * @param pConcept
	 * @param from
	 * @param to
	 * */
	public double getConceptRangeOfPeriodAllOrgs(String conceptValue,
			String payrollValue, String dateFrom, String dateTo) {
		int payroll_id = -1;
		if (payrollValue == null) {
			// payroll_id = getHR_Payroll_ID();
			payroll_id = 0; // all payrrolls
		} else {
			payroll_id = MHRPayroll.forValue(getCtx(), payrollValue).get_ID();
		}
		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);

		if (concept == null)
			return 0.0;

		Timestamp from = null;
		Timestamp to = null;

		if (dateFrom != null)
			from = Timestamp.valueOf(dateFrom);
		if (dateTo != null)
			to = Timestamp.valueOf(dateTo);

		// Detect field name
		final String fieldName;
		if (MHRConcept.COLUMNTYPE_Quantity.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Qty;
		} else if (MHRConcept.COLUMNTYPE_Amount.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Amount;
		} else {
			return 0; // TODO: throw exception?
		}
		//
		MHRPeriod p = MHRPeriod.get(getCtx(), getHR_Period_ID());
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_HR_Concept_ID
				+ "=?");
		params.add(concept.get_ID());
		// check partner
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_C_BPartner_ID
				+ "=?");
		params.add(getM_C_BPartner_ID());
		// Adding dates
		whereClause.append(" AND validTo BETWEEN ? AND ?");
		if (from == null)
			from = getFirstDayOfPeriod(p.getHR_Period_ID());
		if (to == null)
			to = getLastDayOfPeriod(p.getHR_Period_ID());
		params.add(from);
		params.add(to);
		//
		// check process and payroll
		if (payroll_id > 0) {
			whereClause
			.append(" AND EXISTS (SELECT 1 FROM HR_Process p"
					+ " INNER JOIN HR_Period pr ON (pr.HR_Period_id=p.HR_Period_ID)"
					+ " WHERE HR_Movement.HR_Process_ID = p.HR_Process_ID"
					+ " AND p.HR_Payroll_ID=?");

			params.add(payroll_id);
			whereClause.append(")");
			//
		}
		StringBuffer sql = new StringBuffer("SELECT COALESCE(SUM(")
				.append(fieldName).append("),0) FROM ")
				.append(MHRMovement.Table_Name).append(" WHERE ")
				.append(whereClause);
		BigDecimal value = DB.getSQLValueBDEx(get_TrxName(), sql.toString(),
				params);
		if (value != null)
			return value.doubleValue();
		else
			return 0;

	} // getConceptRangeOfPeriod
	
	

	/**
	 * Helper Method: gets Commission summary value of history between 2 dates
	 * if dates range are null then set them based on start and end of period
	 * 
	 * @param from
	 * @param to
	 * */
	public double getCommissionHistory(Timestamp from, Timestamp to) {

		MHRPeriod p = MHRPeriod.get(getCtx(), getHR_Period_ID());
		MHREmployee e = MHREmployee.getActiveEmployee(getCtx(),
				m_C_BPartner_ID, getAD_Org_ID(), get_TrxName());

		// TODO: throw exception?
		if (from == null)
			from = p.getStartDate();
		if (to == null)
			to = p.getEndDate();

		BigDecimal value = DB
				.getSQLValueBD(
						null,
						"SELECT COALESCE(SUM(cr.grandtotal),0) "
								+ "FROM C_Commission c "
								+ "JOIN c_CommissionRun cr on c.C_Commission_ID = cr.C_Commission_ID "
								+ "WHERE c.AD_Client_ID = ? AND c.AD_ORG_ID = ? AND c.C_BPartner_ID = ? "
								+ "AND startdate BETWEEN ? AND ? GROUP BY c.AD_Client_ID, c.AD_ORG_ID, "
								+ "c.C_BPartner_ID",
								e.getAD_Client_ID(), e.getAD_Org_ID(), m_C_BPartner_ID,
								from, to);

		if (value == null)
			value = Env.ZERO;

		return value.doubleValue();

	} // getCommissionHistory

	/**
	 * Helper Method: gets Commission summary value of history between 2 dates
	 * if dates range are null then set them based on start and end of period
	 * 
	 * @param bpfilter
	 * */
	public double getFamilyCharge(boolean bpfilter) {
		MHREmployee e = MHREmployee.getActiveEmployee(getCtx(),
				m_C_BPartner_ID, getAD_Org_ID(), get_TrxName());

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer();

		whereClause.append("AD_Client_ID = ?");
		params.add(e.getAD_Client_ID());
		whereClause.append(" AND AD_Org_ID = ?");
		params.add(e.getAD_Org_ID());
		if (bpfilter) {
			whereClause.append(" AND C_BPartner_ID = ?");
			params.add(m_C_BPartner_ID);
		}
		whereClause.append(" AND IsInPayroll = 'Y' AND IsActive = 'Y'");
		// TODO Needed for Sismode customization
		// whereClause.append(" AND IsFamilyCharge = 'Y'");
		StringBuffer sql = new StringBuffer("SELECT COUNT(*) FROM AD_User ")
				.append(" WHERE ").append(whereClause);

		BigDecimal value = DB.getSQLValueBDEx(get_TrxName(), sql.toString(),
				params);

		if (value == null)
			value = Env.ZERO;

		return value.doubleValue();

	} // getFamilyCharge

	/**
	 * Helper Method : Attribute that had from some date to another to date, if
	 * it finds just one period it's seen for the attribute of such period if
	 * there are two or more attributes based on the days
	 * 
	 * @param ctx
	 * @param vAttribute
	 * @param dateFrom
	 * @param dateTo
	 * @return attribute value
	 */
	public double getAttribute(Properties ctx, String vAttribute,
			Timestamp dateFrom, Timestamp dateTo) {
		// TODO ???
		log.warning("not implemented yet -> getAttribute (Properties, String, Timestamp, Timestamp)");
		return 0;
	} // getAttribute

	/**
	 * Helper Method : Attribute that had from some period to another to period,
	 * periods with values 0 -1 1, etc. actual previous one period, next period
	 * 0 corresponds to actual period Value of HR_Attribute if it finds just one
	 * period it's seen for the attribute of such period if there are two or
	 * more attributes pFrom and pTo the search is done by the period value, it
	 * helps to search from previous year based on the days
	 * 
	 * @param ctx
	 * @param vAttribute
	 * @param periodFrom
	 * @param periodTo
	 * @param pFrom
	 * @param pTo
	 * @return attribute value
	 */
	public double getAttribute(Properties ctx, String vAttribute,
			int periodFrom, int periodTo, String pFrom, String pTo) {
		// TODO ???
		log.warning("not implemented yet -> getAttribute (Properties, String, int, int, String, String)");
		return 0;
	} // getAttribute

	/**
	 * Helper Method : Get AttributeInvoice
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @return C_Invoice_ID, 0 if does't
	 */
	public int getAttributeInvoice(String pConcept) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(m_dateFrom);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID"
				+ " AND c.Value = ?)");
		params.add(pConcept);
		//
		if (!MHRConcept.TYPE_Information.equals(concept.getType())) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizaci��n Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();

		if (attribute != null)
			return (Integer) attribute.get_Value("C_Invoice_ID");
		else
			return 0;

	} // getAttributeInvoice

	/**
	 * Helper Method : Get AttributeDocType
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @return C_DocType_ID, 0 if does't
	 */
	public int getAttributeDocType(String pConcept) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;

		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(m_dateFrom);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID"
				+ " AND c.Value = ?)");
		params.add(pConcept);
		//
		if (!MHRConcept.TYPE_Information.equals(concept.getType())) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizaci��n Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute

		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();

		if (attribute != null)
			return (Integer) attribute.get_Value("C_DocType_ID");
		else
			return 0;

	} // getAttributeDocType

	/**
	 * Helper Method : get days from specific period
	 * 
	 * @param period
	 * @return no. of days
	 */
	public double getDays(int period) {
		/*
		 * TODO: This getter could have an error as it's not using the
		 * parameter, and it doesn't what is specified in help
		 */
		log.warning("instead of using getDays in the formula it's recommended to use _DaysPeriod+1");
		return Env.getContextAsInt(getCtx(), "_DaysPeriod") + 1;
	} // getDays

	/**
	 * Helper Method : get actual period
	 * 
	 * @param N
	 *            /A
	 * @return period id
	 */
	public int getPayrollPeriod() {

		MHRPeriod p = MHRPeriod.get(getCtx(), getHR_Period_ID());
		return p.getHR_Period_ID();

	} // getPayrollPeriod

	/**
	 * Helper Method : get first date from specific period
	 * 
	 * @param period
	 * @return date from
	 */
	public Timestamp getFirstDayOfPeriod(int period_id) {

		MHRPeriod period = new MHRPeriod(getCtx(), period_id, get_TrxName());
		Calendar firstdayofperiod = Calendar.getInstance();
		Timestamp datefromofperiod = period.getStartDate();
		firstdayofperiod.setTime(datefromofperiod);
		firstdayofperiod.set(Calendar.DAY_OF_MONTH, 1);
		datefromofperiod.setTime(firstdayofperiod.getTimeInMillis());
		return datefromofperiod;

	} // getFirstDayOfPeriod

	/**
	 * Helper Method : Date of the first period of the employee in the system
	 * 
	 * @param period
	 * @return date from
	 */
	public Timestamp getFirstDayOfFistPeriodOfEmployee(int employeeId,
			int payrollID) {
		Timestamp date = null;
		String sQuery = "Select COALESCE((Select pd.StartDate From HR_Period pd Where pd.HR_Period_ID = "
				+ " COALESCE((Select MIN(pr.HR_Period_ID) from HR_Process pr "
				+ " JOIN HR_Movement m ON pr.HR_Process_ID = m.HR_Process_ID AND m.C_BPartner_ID = ? "
				+ " where pr.HR_Payroll_ID = ?),0)),now())";
		date = DB.getSQLValueTS(get_TrxName(), sQuery, new Object[] {
				employeeId, payrollID });

		return date;

	} // getFirstDayOfPeriod

	/**
	 * Helper Method : get last date to specific period
	 * 
	 * @param period
	 * @return date to
	 */
	public Timestamp getLastDayOfPeriod(int period_id) {

		MHRPeriod period = new MHRPeriod(getCtx(), period_id, get_TrxName());
		Calendar firstdayofperiod = Calendar.getInstance();
		Timestamp datetoofperiod = period.getEndDate();
		firstdayofperiod.setTime(datetoofperiod);
		firstdayofperiod.set(Calendar.DAY_OF_MONTH,
				firstdayofperiod.getActualMaximum(Calendar.DAY_OF_MONTH));
		datetoofperiod.setTime(firstdayofperiod.getTimeInMillis());
		return datetoofperiod;

	} // getLastDayOfPeriod

	/**
	 * Helper Method : get first year date from specific period
	 * 
	 * @param period
	 * @return date from
	 */
	public Timestamp getFirstDayOfPeriodYear(int period_id) {

		MHRPeriod period = new MHRPeriod(getCtx(), period_id, get_TrxName());
		Calendar firstdayofperiod = Calendar.getInstance();
		Timestamp datefromofperiod = period.getStartDate();
		firstdayofperiod.setTime(datefromofperiod);
		firstdayofperiod.set(Calendar.DAY_OF_YEAR, 1);
		datefromofperiod.setTime(firstdayofperiod.getTimeInMillis());
		return datefromofperiod;

	} // getFirstDayOfPeriodYear

	/**
	 * Helper Method : get last year date to specific period
	 * 
	 * @param period
	 * @return date to
	 */
	public Timestamp getLastDayOfPeriodYear(int period_id) {

		MHRPeriod period = new MHRPeriod(getCtx(), period_id, get_TrxName());
		Calendar firstdayofperiod = Calendar.getInstance();
		Timestamp datetoofperiod = period.getEndDate();
		firstdayofperiod.setTime(datetoofperiod);
		firstdayofperiod.set(Calendar.DAY_OF_YEAR,
				firstdayofperiod.getActualMaximum(Calendar.DAY_OF_YEAR));
		datetoofperiod.setTime(firstdayofperiod.getTimeInMillis());
		return datetoofperiod;

	} // getLastDayOfPeriodYear

	/**
	 * Helper Method : get first history date from specific period
	 * 
	 * @param period
	 *            , servicedate, months
	 * @return date from
	 */
	public Timestamp getFirstDayOfPeriodHistory(int period_id,
			Timestamp servicedate, Integer months) {

		if (months == null)
			months = 12;

		MHRPeriod period = new MHRPeriod(getCtx(), period_id, get_TrxName());
		Calendar firstdayofhistory = Calendar.getInstance();
		Timestamp datefromofhistory = period.getStartDate();
		firstdayofhistory.setTime(datefromofhistory);
		firstdayofhistory.add(Calendar.MONTH, months * -1);
		firstdayofhistory.set(Calendar.DAY_OF_MONTH, 1);
		datefromofhistory.setTime(firstdayofhistory.getTimeInMillis());

		if (servicedate != null && datefromofhistory.before(servicedate))
			return servicedate;

		return datefromofhistory;

	} // getFirstDayOfPeriodHistory

	/**
	 * Helper Method : get first history date from specific period
	 * 
	 * @param period
	 *            , servicedate, months
	 * @return date to
	 */
	public Timestamp getLastDayOfPeriodHistory(int period_id,
			Timestamp servicedate, Integer months) {

		if (months == null)
			months = 1;

		MHRPeriod period = new MHRPeriod(getCtx(), period_id, get_TrxName());
		Calendar lastdayofhistory = Calendar.getInstance();
		Timestamp datetoofhistory = period.getStartDate();
		lastdayofhistory.setTime(datetoofhistory);
		lastdayofhistory.add(Calendar.MONTH, months * -1);
		lastdayofhistory.set(Calendar.DAY_OF_MONTH,
				lastdayofhistory.getActualMaximum(Calendar.DAY_OF_MONTH));
		datetoofhistory.setTime(lastdayofhistory.getTimeInMillis());

		if (servicedate != null && datetoofhistory.before(servicedate))
			return servicedate;

		return datetoofhistory;

	} // getLastDayOfPeriodHistory

	/**
	 * Helper Method : get timestamp date
	 * 
	 * @param sdate
	 * @return sdate Timestamp
	 */
	public Timestamp getStringToTimestamp(String sdate) {
		return Timestamp.valueOf(sdate);
	} // getStringToTimestamp

	/**
	 * Helper Method : get string date
	 * 
	 * @param tsdate
	 * @return tsdate String
	 */
	public String getTimestampToString(Timestamp tsdate) {
		return tsdate.toString();
	} // getTimestampToString

	public int getM_C_BPartner_ID() {
		return m_C_BPartner_ID;

	}

	public void setM_C_BPartner_ID(int m_C_BPartner_ID) {
		this.m_C_BPartner_ID = m_C_BPartner_ID;
	}

	/**
	 * Helper Method : It is calculated as the excess deductions which must be
	 * sent to the following period because otherwise the payment would be
	 * negative
	 * 
	 * @param int p_Process
	 * @param int p_C_BPartner_ID
	 * @param int double currentCredit
	 * @param String
	 *            p_conceptValueFrom,String p_coneptValueTo,String
	 *            p_conceptCreditAcum,String p_conceptRevenue
	 * @return double Credit For Next Period
	 */
	public double getCreditForNextPeriod(int p_Process, int p_C_BPartner_ID,
			double currentCredit, String p_conceptValueFrom,
			String p_coneptValueTo, String p_conceptCreditAcum,
			String p_conceptRevenue) {
		double debitTotal = 0;
		double creditTotal = 0;
		currentCredit = Math.rint(currentCredit * 100) / 100;
		MHRConcept c = MHRConcept.forValue(getCtx(), p_conceptCreditAcum);
		MHRMovement m = m_movement.get(c.get_ID());
		if (m == null) {
			createMovementFromConcept(c, c.isPrinted());
			m = m_movement.get(c.get_ID());
		}
		creditTotal = m.getAmount().doubleValue();
		if (creditTotal == -1) {
			creditTotal = currentCredit;
		} else {
			creditTotal += currentCredit;
		}

		MHRConcept d = MHRConcept.forValue(getCtx(), p_conceptRevenue);
		MHRMovement md = m_movement.get(d.get_ID());
		if (md != null)
			debitTotal = md.getAmount().doubleValue();

		if (creditTotal > debitTotal) {
			if (p_conceptValueFrom != null && p_coneptValueTo != null) {
				if (!p_conceptValueFrom.isEmpty() && !p_coneptValueTo.isEmpty()) {
					// Register attribute for nex period
					MHRConcept conceptFrom = new Query(getCtx(),
							MHRConcept.Table_Name, "Value = ?", get_TrxName())
							.setParameters(p_conceptValueFrom).first();
					MHRConcept conceptTo = new Query(getCtx(),
							MHRConcept.Table_Name, "Value = ?", get_TrxName())
							.setParameters(p_coneptValueTo).first();
					MHRPeriod periodFrom = (MHRPeriod) this.getHR_Period();
					Timestamp periodFromDateAcct  =periodFrom.getDateAcct();
					String where = MHRPayroll.COLUMNNAME_HR_Payroll_ID
							+ " = ? AND DateAcct > ?";
					Object[] para = new Object[] {
							periodFrom.getHR_Payroll_ID(), periodFromDateAcct };
					Timestamp DateAcctTo = new Query(getCtx(),
							MHRPeriod.Table_Name, where, get_TrxName())
							.setParameters(para).aggregate("DateAcct", "MIN",Timestamp.class);
					if (DateAcctTo != null) {
						where = MHRPayroll.COLUMNNAME_HR_Payroll_ID
								+ " = ? AND DateAcct = ?";
						para = new Object[] { periodFrom.getHR_Payroll_ID(), DateAcctTo };
						MHRPeriod periodTo = new Query(getCtx(),
								MHRPeriod.Table_Name, where, get_TrxName())
								.setParameters(para).first();
						if (periodTo != null) {

							//							MHRAttribute attribute = new MHRAttribute(getCtx(),
							//									0, get_TrxName());
							//							attribute.setHR_Concept_ID(conceptTo.get_ID());
							//							attribute.setDescription(Msg.translate(getCtx(),
							//									"Missing Credit From Last Period")
							//									+ " "
							//									+ conceptFrom.getName());
							//							attribute.setAmount(BigDecimal.valueOf(creditTotal
							//									- debitTotal));
							//							attribute.setValidFrom(periodTo.getStartDate());
							//							attribute.setValidTo(periodTo.getEndDate());
							//							attribute.setC_BPartner_ID(p_C_BPartner_ID);
							//							attribute.setColumnType(conceptTo.getColumnType());
							//							attribute.saveEx();
							getaddAttributeAmt(
									conceptFrom.getValue(),
									conceptTo.getValue(),
									periodTo.get_ID(),
									(creditTotal - debitTotal),
									(Msg.translate(getCtx(),
											"Missing Credit From Last Period")
											+ " " + conceptFrom.getName()),
									p_C_BPartner_ID);
						}

					}

				}
			}
			updateConcept(p_conceptCreditAcum, (debitTotal));
			return creditTotal - debitTotal;
		} else {
			updateConcept(p_conceptCreditAcum, (creditTotal));
			return 0;
		}
	}// getCreditForNextPeriod

	public String getMessageCreditForNextPeriod(double creditForNextPeriod) {
		if (creditForNextPeriod > 0) {
			return Msg.translate(getCtx(), " (Credit For Next Period)") + ": "
					+ creditForNextPeriod;
		} else {
			return "";
		}

	}

	/**
	 * Helper Method : update the value of a concept
	 * 
	 * @param conceptValue
	 * @param value
	 */
	public void updateConcept(String conceptValue, double value) {
		try {

			MHRConcept concept = MHRConcept.forValue(getCtx(),
					conceptValue.trim());

			MHRMovement m = m_movement.get(concept.get_ID());
			if (m == null) {
				createMovementFromConcept(concept, concept.isPrinted());
				m = m_movement.get(concept.get_ID());
			}

			m.setAmount(BigDecimal.valueOf(value));
			m.saveEx();
		} catch (Exception e) {
			s_log.warning(e.getMessage());
		}
	} // setConcept

	/**
	 * Helper Method : getNexPeriod by PeriodNo
	 * 
	 * @param p_currentPeriod_ID
	 * @return periodTo_ID
	 */
	public int getNexPeriod(int p_currentPeriod_ID) {
		MHRPeriod periodFrom = MHRPeriod.get(getCtx(), p_currentPeriod_ID);
		MHRPeriod periodTo = null;


		Timestamp dateAcctFrom = periodFrom.getDateAcct();
		String where = MHRPayroll.COLUMNNAME_HR_Payroll_ID
				+ " = ? AND DateAcct > ?";
		Object[] para = new Object[] { periodFrom.getHR_Payroll_ID(),dateAcctFrom };
		Timestamp DateAcctTo = new Query(getCtx(), MHRPeriod.Table_Name,
				where, get_TrxName()).setParameters(para).aggregate("DateAcct",
						"MIN",Timestamp.class);
		if (DateAcctTo != null) {

			where = MHRPayroll.COLUMNNAME_HR_Payroll_ID
					+ " = ? AND DateAcct = ?";
			para = new Object[] { periodFrom.getHR_Payroll_ID(),DateAcctTo };
			periodTo = new Query(getCtx(), MHRPeriod.Table_Name, where,
					get_TrxName()).setParameters(para).first();

		}
		return periodTo.get_ID();
	}

	/**
	 * Helper Method : getaddAttributeAmt (New register on HR_Attribute)
	 * 
	 * @param p_currentPeriod
	 * @return periodTo
	 */
	public void getaddAttributeAmt(String p_concept, int p_period_id,
			double p_amt, String p_description, int p_C_BPartner_ID) {

		MHRPeriod p_period = MHRPeriod.get(getCtx(), p_period_id);
		MHRConcept conceptTo = MHRConcept.forValue(getCtx(), p_concept);

		MHRAttribute attribute = new MHRAttribute(getCtx(), 0, get_TrxName());
		attribute.setHR_Concept_ID(conceptTo.get_ID());
		attribute.setDescription(p_description);
		attribute.setAmount(BigDecimal.valueOf(p_amt));
		attribute.setValidFrom(p_period.getStartDate());
		attribute.setValidTo(p_period.getEndDate());
		attribute.setColumnType(conceptTo.getColumnType());
		attribute.setC_BPartner_ID(p_C_BPartner_ID);
		attribute.saveEx();

	}// getaddAttributeAmt

	/**
	 * 
	 * @param p_conceptFrom
	 * @param p_conceptTo
	 * @param p_period_id
	 * @param p_amt
	 * @param p_description
	 * @param p_C_BPartner_ID
	 */
	public void getaddAttributeAmt(String p_conceptFrom, String p_conceptTo,
			int p_period_id, double p_amt, String p_description,
			int p_C_BPartner_ID) {

		MHRPeriod p_period = MHRPeriod.get(getCtx(), p_period_id);
		MHRConcept conceptFrom = MHRConcept.forValue(getCtx(), p_conceptFrom);
		MHRConcept conceptTo = MHRConcept.forValue(getCtx(), p_conceptTo);
		String where = " HR_Process_ID = ? AND HR_Concept_ID = ? AND C_BPartner_ID = ? AND HR_ConceptFrom_ID = ?";
		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				where, get_TrxName()).setParameters(getHR_Process_ID(),
						conceptTo.get_ID(), p_C_BPartner_ID, conceptFrom.get_ID())
				.firstOnly();

		if (attribute == null)
			attribute = new MHRAttribute(getCtx(), 0, get_TrxName());

		attribute.setHR_Concept_ID(conceptTo.get_ID());
		attribute.setDescription(p_description);
		attribute.setAmount(BigDecimal.valueOf(p_amt));
		attribute.setValidFrom(p_period.getStartDate());
		attribute.setValidTo(p_period.getEndDate());
		attribute.setColumnType(conceptTo.getColumnType());
		attribute.setC_BPartner_ID(p_C_BPartner_ID);
		attribute.set_ValueOfColumn("HR_Process_ID", getHR_Process_ID());
		attribute.set_ValueOfColumn("HR_ConceptFrom_ID", conceptFrom.get_ID());
		attribute.saveEx();

	}// getaddAttributeAmt

	/**
	 * Helper Method : Get Attribute [get Attribute to search key concept and
	 * date ]
	 * 
	 * @param pConcept
	 *            - Value to Concept
	 * @param date1
	 * @param date2
	 * @return Amount of concept, applying to employee
	 */
	public double getAttributeDaysBetween(String pConcept, Timestamp date1, Timestamp date2) {
		MHRConcept concept = MHRConcept.forValue(getCtx(), pConcept);
		if (concept == null)
			return 0;
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuilder whereClause = new StringBuilder();
		// check ValidFrom:
		whereClause.append(MHRAttribute.COLUMNNAME_ValidFrom + "<=?");
		params.add(date2);
		// check client
		whereClause.append(" AND AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause
		.append(" AND EXISTS (SELECT 1 FROM HR_Concept c WHERE c.HR_Concept_ID=HR_Attribute.HR_Concept_ID AND HR_Attribute.IsActive='Y' AND c.Value = ? "
				+ " AND (HR_Attribute.validto IS NULL OR HR_Attribute.validto >= ?) )");
		params.add(pConcept);
		params.add(date1);
		//
		if (!concept.getType().equals(MHRConcept.TYPE_Information)) {
			whereClause.append(" AND " + MHRAttribute.COLUMNNAME_C_BPartner_ID
					+ " = ?");
			params.add(m_C_BPartner_ID);
		}
		// LVE Localizacion Venezuela
		// when is employee, it is necessary to check if the organization of the
		// employee is equal to that of the attribute
		// if (concept.isEmployee()){
		whereClause.append(" AND ( " + MHRAttribute.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRAttribute.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());
		// }

		MHRAttribute attribute = new Query(getCtx(), MHRAttribute.Table_Name,
				whereClause.toString(), get_TrxName()).setParameters(params)
				// .setOrderBy(MHRAttribute.COLUMNNAME_ValidFrom + " DESC")
				.setOrderBy(MHRAttribute.COLUMNNAME_AD_Org_ID + " DESC")
				.first();
		if (attribute != null){
			if (attribute.getValidTo()!=null)
				return getDays(attribute.getValidFrom(),attribute.getValidTo());
		}


		// something else
		return 0.0; // TODO throw exception ??
	} // getAttribute

	/**
	 * TODO QSS Reviewme Helper Method: gets Concept value of payrroll(s)
	 * between 2 dates if payrollValue is null then sum all payrolls between 2
	 * dates if dates range are null then set them based on first and last day
	 * of period
	 * 
	 * @param pConcept
	 * @param from
	 * @param to
	 * */
	public double getConceptRangeOfPeriodExcludeFirstAndLast(String conceptValue,
			String payrollValue, String dateFrom, String dateTo) {
		int payroll_id = -1;
		if (payrollValue == null) {
			// payroll_id = getHR_Payroll_ID();
			payroll_id = 0; // all payrrolls
		} else {
			payroll_id = MHRPayroll.forValue(getCtx(), payrollValue).get_ID();
		}

		MHRConcept concept = MHRConcept.forValue(getCtx(), conceptValue);

		if (concept == null)
			return 0.0;

		Timestamp from = null;
		Timestamp to = null;

		if (dateFrom != null)
			from = Timestamp.valueOf(dateFrom);
		if (dateTo != null)
			to = Timestamp.valueOf(dateTo);

		// Detect field name
		final String fieldName;
		if (MHRConcept.COLUMNTYPE_Quantity.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Qty;
		} else if (MHRConcept.COLUMNTYPE_Amount.equals(concept.getColumnType())) {
			fieldName = MHRMovement.COLUMNNAME_Amount;
		} else {
			return 0; // TODO: throw exception?
		}
		//
		MHRPeriod p = MHRPeriod.get(getCtx(), getHR_Period_ID());
		ArrayList<Object> params = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer();
		// check client
		whereClause.append("AD_Client_ID = ?");
		params.add(getAD_Client_ID());
		// check concept
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_HR_Concept_ID
				+ "=?");
		params.add(concept.get_ID());
		// check partner
		whereClause.append(" AND " + MHRMovement.COLUMNNAME_C_BPartner_ID
				+ "=?");
		params.add(getM_C_BPartner_ID());
		// Adding Organization
		whereClause.append(" AND ( " + MHRMovement.COLUMNNAME_AD_Org_ID
				+ "=? OR " + MHRMovement.COLUMNNAME_AD_Org_ID + "= 0 )");
		params.add(getAD_Org_ID());
		// Adding dates
		whereClause.append(" AND validFrom >= ? ");
		params.add(from);
		whereClause.append(" AND validTo <= ? ");
		params.add(to);

		//		if (from == null)
		//			from = getFirstDayOfPeriod(p.getHR_Period_ID());
		//		if (to == null)
		//			to = getLastDayOfPeriod(p.getHR_Period_ID());
		//		params.add(from);
		//		params.add(to);
		//
		// check process and payroll
		if (payroll_id > 0) {
			whereClause
			.append(" AND EXISTS (SELECT 1 FROM HR_Process p"
					+ " INNER JOIN HR_Period pr ON (pr.HR_Period_id=p.HR_Period_ID)"
					+ " WHERE HR_Movement.HR_Process_ID = p.HR_Process_ID"
					+ " AND p.HR_Payroll_ID=?");

			params.add(payroll_id);
			whereClause.append(")");
			//
		}
		StringBuffer sql = new StringBuffer("SELECT COALESCE(SUM(")
				.append(fieldName).append("),0) FROM ")
				.append(MHRMovement.Table_Name).append(" WHERE ")
				.append(whereClause);
		BigDecimal value = DB.getSQLValueBDEx(get_TrxName(), sql.toString(),
				params);
		if (value != null)
			return value.doubleValue();
		else
			return 0;

	} // getConceptRangeOfPeriod

	public Timestamp[] getDates2 (Timestamp DateFrom, Timestamp DateTo) {

		Integer cutDay = MSysConfig.getIntValue("CutDayPayrollEvents", 20, getAD_Client_ID());
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(m_dateFrom);
		calendar.add(Calendar.MONTH,-1);
		calendar.add(Calendar.DAY_OF_MONTH,cutDay);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Timestamp m_DateFrom2 = Timestamp.valueOf(sdf.format(calendar.getTime()).toString());
		calendar = Calendar.getInstance();
		calendar.setTime(m_dateFrom);
		calendar.add(Calendar.DAY_OF_YEAR, cutDay-1);
		Timestamp m_DateTo2 = Timestamp.valueOf(sdf.format(calendar.getTime()).toString());	

		return new Timestamp[] {m_DateFrom2, m_DateTo2};		
	} //getDates2

	public static int DaysTotal(Date Major, Date Minor) {
		long diferenciaEn_ms = Major.getTime() - Minor.getTime();
		long dias = diferenciaEn_ms / (1000 * 60 * 60 * 24);
		return (int) dias+1;
	}	

} // MHRProcess
