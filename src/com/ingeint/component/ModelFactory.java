/**
 * This file is part of iDempiere ERP <http://www.idempiere.org>.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Copyright (C) 2015 INGEINT <http://www.ingeint.com>.
 * Copyright (C) Contributors.
 * 
 * Contributors:
 *    - 2015 Saúl Piña <spina@ingeint.com>.
 *    - 2015 Freddy Heredia <freddy.heredia@ingeint.com>.
 */

package com.ingeint.component;

import org.eevolution.model.MHRAttribute;
import org.eevolution.model.MHRConcept;
import org.eevolution.model.MHRConceptCategory;
import org.eevolution.model.MHRDepartment;
import org.eevolution.model.MHREmployee;
import org.eevolution.model.MHRMovement;
import org.eevolution.model.MHRPayroll;
import org.eevolution.model.MHRPayrollConcept;
import org.eevolution.model.MHRPeriod;
import org.eevolution.model.MHRProcess;
import org.eevolution.model.MHRYear;

import com.ingeint.base.CustomModelFactory;
import com.ingeint.model.MHRLoan;
import com.ingeint.model.MHRLoanLines;
import com.ingeint.model.MHRPaymentSelection;
import com.ingeint.model.MHRPaymentSelectionLine;
import com.ingeint.model.MHRSectorCode;
import com.ingeint.model.MHR_Basic_Factor_Type;
import com.ingeint.model.MHR_GAP;
import com.ingeint.model.MPaymentSelectionType;


/**
 * Model Factory
 */
public class ModelFactory extends CustomModelFactory {

	@Override
	protected void initialize() {
		// Register the models class to build
		registerTableModel(MHR_Basic_Factor_Type.Table_Name, MHR_Basic_Factor_Type.class);
		registerTableModel(MHR_GAP.Table_Name, MHR_GAP.class);
		registerTableModel(MHRPeriod.Table_Name, MHRPeriod.class);
		registerTableModel(MHRAttribute.Table_Name, MHRAttribute.class);
		registerTableModel(MHRConcept.Table_Name, MHRConcept.class);
		registerTableModel(MHRConceptCategory.Table_Name, MHRConceptCategory.class);
		registerTableModel(MHRDepartment.Table_Name, MHRDepartment.class);
		registerTableModel(MHREmployee.Table_Name, MHREmployee.class);
		registerTableModel(MHRMovement.Table_Name, MHRMovement.class);
		registerTableModel(MHRPayroll.Table_Name, MHRPayroll.class);
		registerTableModel(MHRPayrollConcept.Table_Name, MHRPayrollConcept.class);
		registerTableModel(MHRProcess.Table_Name, MHRProcess.class);
		registerTableModel(MHRYear.Table_Name, MHRYear.class);
		registerTableModel(MHRLoan.Table_Name, MHRLoan.class);
		registerTableModel(MHRLoanLines.Table_Name, MHRLoanLines.class);
		registerTableModel(MHRPaymentSelection.Table_Name, MHRPaymentSelection.class);
		registerTableModel(MHRPaymentSelectionLine.Table_Name, MHRPaymentSelectionLine.class);
		registerTableModel (MHRSectorCode.Table_Name, MHRSectorCode.class);
		registerTableModel(MPaymentSelectionType.Table_Name, MPaymentSelectionType.class);
	}
}
