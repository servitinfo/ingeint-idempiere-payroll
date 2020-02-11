/******************************************************************************
 * Product: iDempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2012 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
/** Generated Model - DO NOT CHANGE */
package com.ingeint.model;

import java.sql.ResultSet;
import java.util.Properties;
import org.compiere.model.*;
import org.compiere.util.KeyNamePair;

/** Generated Model for ING_PaymentSelectionType
 *  @author iDempiere (generated) 
 *  @version Release 7.1 - $Id$ */
public class X_ING_PaymentSelectionType extends PO implements I_ING_PaymentSelectionType, I_Persistent 
{

	/**
	 *
	 */
	private static final long serialVersionUID = 20200210L;

    /** Standard Constructor */
    public X_ING_PaymentSelectionType (Properties ctx, int ING_PaymentSelectionType_ID, String trxName)
    {
      super (ctx, ING_PaymentSelectionType_ID, trxName);
      /** if (ING_PaymentSelectionType_ID == 0)
        {
			setHR_Concept_ID (0);
			setING_PaymentSelectionType_ID (0);
			setName (null);
        } */
    }

    /** Load Constructor */
    public X_ING_PaymentSelectionType (Properties ctx, ResultSet rs, String trxName)
    {
      super (ctx, rs, trxName);
    }

    /** AccessLevel
      * @return 3 - Client - Org 
      */
    protected int get_AccessLevel()
    {
      return accessLevel.intValue();
    }

    /** Load Meta Data */
    protected POInfo initPO (Properties ctx)
    {
      POInfo poi = POInfo.getPOInfo (ctx, Table_ID, get_TrxName());
      return poi;
    }

    public String toString()
    {
      StringBuffer sb = new StringBuffer ("X_ING_PaymentSelectionType[")
        .append(get_ID()).append("]");
      return sb.toString();
    }

	/** Set Description.
		@param Description 
		Optional short description of the record
	  */
	public void setDescription (String Description)
	{
		set_Value (COLUMNNAME_Description, Description);
	}

	/** Get Description.
		@return Optional short description of the record
	  */
	public String getDescription () 
	{
		return (String)get_Value(COLUMNNAME_Description);
	}

	/** Set Comment/Help.
		@param Help 
		Comment or Hint
	  */
	public void setHelp (String Help)
	{
		set_Value (COLUMNNAME_Help, Help);
	}

	/** Get Comment/Help.
		@return Comment or Hint
	  */
	public String getHelp () 
	{
		return (String)get_Value(COLUMNNAME_Help);
	}

	public org.eevolution.model.I_HR_Concept getHR_Concept() throws RuntimeException
    {
		return (org.eevolution.model.I_HR_Concept)MTable.get(getCtx(), org.eevolution.model.I_HR_Concept.Table_Name)
			.getPO(getHR_Concept_ID(), get_TrxName());	}

	/** Set Payroll Concept.
		@param HR_Concept_ID Payroll Concept	  */
	public void setHR_Concept_ID (int HR_Concept_ID)
	{
		if (HR_Concept_ID < 1) 
			set_Value (COLUMNNAME_HR_Concept_ID, null);
		else 
			set_Value (COLUMNNAME_HR_Concept_ID, Integer.valueOf(HR_Concept_ID));
	}

	/** Get Payroll Concept.
		@return Payroll Concept	  */
	public int getHR_Concept_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_HR_Concept_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set ING_PaymentSelectionType.
		@param ING_PaymentSelectionType_ID ING_PaymentSelectionType	  */
	public void setING_PaymentSelectionType_ID (int ING_PaymentSelectionType_ID)
	{
		if (ING_PaymentSelectionType_ID < 1) 
			set_ValueNoCheck (COLUMNNAME_ING_PaymentSelectionType_ID, null);
		else 
			set_ValueNoCheck (COLUMNNAME_ING_PaymentSelectionType_ID, Integer.valueOf(ING_PaymentSelectionType_ID));
	}

	/** Get ING_PaymentSelectionType.
		@return ING_PaymentSelectionType	  */
	public int getING_PaymentSelectionType_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_ING_PaymentSelectionType_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set ING_PaymentSelectionType_UU.
		@param ING_PaymentSelectionType_UU ING_PaymentSelectionType_UU	  */
	public void setING_PaymentSelectionType_UU (String ING_PaymentSelectionType_UU)
	{
		set_Value (COLUMNNAME_ING_PaymentSelectionType_UU, ING_PaymentSelectionType_UU);
	}

	/** Get ING_PaymentSelectionType_UU.
		@return ING_PaymentSelectionType_UU	  */
	public String getING_PaymentSelectionType_UU () 
	{
		return (String)get_Value(COLUMNNAME_ING_PaymentSelectionType_UU);
	}

	/** Set Name.
		@param Name 
		Alphanumeric identifier of the entity
	  */
	public void setName (String Name)
	{
		set_Value (COLUMNNAME_Name, Name);
	}

	/** Get Name.
		@return Alphanumeric identifier of the entity
	  */
	public String getName () 
	{
		return (String)get_Value(COLUMNNAME_Name);
	}

    /** Get Record ID/ColumnName
        @return ID/ColumnName pair
      */
    public KeyNamePair getKeyNamePair() 
    {
        return new KeyNamePair(get_ID(), getName());
    }
}