/*
 * $Id: TabyPlacementImportFileHandlerBean.java,v 1.16 2006/04/09 12:05:08 laddi Exp $
 *
 * Copyright (C) 2003 Agura IT. All Rights Reserved.
 *
 * This software is the proprietary information of Agura IT AB.
 * Use is subject to license terms.
 *
 */

package se.idega.idegaweb.commune.block.importer.business;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import se.idega.idegaweb.commune.business.CommuneUserBusiness;
import se.idega.idegaweb.commune.care.business.CareBusiness;
import se.idega.idegaweb.commune.care.resource.business.ResourceBusiness;
import se.idega.idegaweb.commune.care.resource.data.Resource;
import se.idega.idegaweb.commune.care.resource.data.ResourceClassMember;

import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.block.school.business.SchoolBusiness;
import com.idega.block.school.data.School;
import com.idega.block.school.data.SchoolClass;
import com.idega.block.school.data.SchoolClassHome;
import com.idega.block.school.data.SchoolClassMember;
import com.idega.block.school.data.SchoolClassMemberHome;
import com.idega.block.school.data.SchoolHome;
import com.idega.block.school.data.SchoolSeason;
import com.idega.block.school.data.SchoolType;
import com.idega.block.school.data.SchoolTypeHome;
import com.idega.block.school.data.SchoolYear;
import com.idega.block.school.data.SchoolYearHome;
import com.idega.business.IBOServiceBean;
import com.idega.core.localisation.data.ICLanguage;
import com.idega.core.localisation.data.ICLanguageHome;
import com.idega.core.location.data.Commune;
import com.idega.core.location.data.CommuneHome;
import com.idega.data.IDOAddRelationshipException;
import com.idega.user.data.Gender;
import com.idega.user.data.GenderHome;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.IWTimestamp;
import com.idega.util.Timer;

/** 
 * Import logic for placing Taby students.
 * <br>
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values (15, 'Taby student placement importer with resources', 
 * 'se.idega.idegaweb.commune.block.importer.business.TabyPlacementImportFileHandlerBean',
 * 'Imports Taby students with resources and students in other communes.')
 * <br>
 * Note that the "15" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>
 * Last modified: $Date: 2006/04/09 12:05:08 $ by $Author: laddi $
 *
 * @author Anders Lindman
 * @version $Revision: 1.16 $
 */
public class TabyPlacementImportFileHandlerBean extends IBOServiceBean implements TabyPlacementImportFileHandler, ImportFileHandler {

	private CommuneUserBusiness biz = null;
	private SchoolBusiness schoolBiz = null;
	private ResourceBusiness resourceBiz = null;
  
	private SchoolYearHome sYearHome = null;
	private SchoolTypeHome sTypeHome = null;
	private SchoolHome sHome = null;
	private SchoolClassHome sClassHome = null;
	private SchoolClassMemberHome sClassMemberHome = null;
	private CommuneHome communeHome = null;
	private ICLanguageHome languageHome = null;

	private SchoolSeason season = null;
    
	private ImportFile file = null;
	private UserTransaction transaction = null;
  
	private List userValues = null;
	private Map failedSchools = null;
	private Map errorLog = null;
	private ArrayList failedRecords = null;

	private Resource motherTongueResource = null;

	//private final static String REGISTER_DATE = "2003-07-01";
		
	private final int COLUMN_SCHOOL_NAME = 0;  
	private final int COLUMN_SCHOOL_TYPE = 1;  
	private final int COLUMN_PERSONAL_ID = 2;  
	private final int COLUMN_SCHOOL_YEAR = 3;  
	private final int COLUMN_SCHOOL_CLASS = 4;
	private final int COLUMN_STUDENT_FIRST_NAME = 5;
	private final int COLUMN_STUDENT_LAST_NAME = 6;
	private final int COLUMN_STUDENT_ADDRESS = 7;  
	private final int COLUMN_STUDENT_ZIP_CODE = 8;  
	private final int COLUMN_STUDENT_ZIP_AREA = 9;
	private final int COLUMN_MOTHER_TONGUE = 10;
	private final int COLUMN_HOME_COMMUNE = 11;
	private final int COLUMN_HOME_COUNTY = 12;

	private final int RESOURCE_ID_NATIVE_LANGUAGE = 1;
	
	private Gender female;
	private Gender male;
	
	//////// cache added by aron //////////
	private Map mapOfSchoolTypes = null;
	private Map mapOfCommunes = null;
	private Map mapOfSchools = null;
	private Map mapOfSchoolRelatedTypes = null;
	private Map mapOfSchoolYears = null;
	private Map mapOfSchoolYearMaps = null;
  
  	/**
  	 * Default constructor.
  	 */
	public TabyPlacementImportFileHandlerBean() {}

	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#handleRecords() 
	 */
	public boolean handleRecords(){
		this.failedRecords = new ArrayList();
		this.failedSchools = new TreeMap();
		this.errorLog = new TreeMap();
		
		// cache initialization 
		this.mapOfSchoolTypes = new HashMap();
		this.mapOfCommunes = new HashMap();
		this.mapOfSchools = new HashMap();
		this.mapOfSchoolRelatedTypes = new HashMap();
		this.mapOfSchoolYears = new HashMap();
		this.mapOfSchoolYearMaps = new HashMap();
		
		this.transaction = this.getSessionContext().getUserTransaction();
        
		Timer clock = new Timer();
		clock.start();

		try {
			//initialize business beans and data homes
			this.biz = (CommuneUserBusiness) this.getServiceInstance(CommuneUserBusiness.class);
			//home = biz.getUserHome();      
			this.schoolBiz = (SchoolBusiness) this.getServiceInstance(SchoolBusiness.class);
			this.resourceBiz = (ResourceBusiness) this.getServiceInstance(ResourceBusiness.class);

			this.sHome = this.schoolBiz.getSchoolHome();           
			this.sYearHome = this.schoolBiz.getSchoolYearHome();
			this.sTypeHome = this.schoolBiz.getSchoolTypeHome();
			this.sClassHome = (SchoolClassHome) this.getIDOHome(SchoolClass.class);
			this.sClassMemberHome = (SchoolClassMemberHome) this.getIDOHome(SchoolClassMember.class);
			this.communeHome = (CommuneHome) this.getIDOHome(Commune.class);
			this.languageHome = (ICLanguageHome) this.getIDOHome(ICLanguage.class);			
			//CareBusiness careBusiness = (CareBusiness)this.getServiceInstance(SchoolChoiceBusiness.class);
			CareBusiness careBusiness = (CareBusiness)this.getServiceInstance(CareBusiness.class);
			//SchoolCategory schoolCat = (SchoolCategoryHome)this.getIDOHome(SchoolCategory.class);

			try {
				//season = schoolBiz.getCurrentSchoolSeason();
				this.season = careBusiness.getCurrentSeason();
				
			} catch(FinderException e) {
				e.printStackTrace();
				System.out.println("TabyPlacementHandler: School season is not defined");
				return false;
			}
      
      		// Get resources (change primary keys to the correct values)
      		
			System.out.println("ID for resource native language = " + this.RESOURCE_ID_NATIVE_LANGUAGE);
			this.motherTongueResource = this.resourceBiz.getResourceByPrimaryKey(new Integer(this.RESOURCE_ID_NATIVE_LANGUAGE));
			if (this.motherTongueResource == null) {
				System.out.println("Resource for mother tongue not found.");
				return false;
			}
      		
			//if the transaction failes all the users and their relations are removed
			this.transaction.begin();

			//iterate through the records and process them
			String item;
			int count = 0;
			boolean failed = false;

			while (!(item = (String) this.file.getNextRecord()).equals("")) {
				count++;
				
				if(!processRecord(item, count)) {
					this.failedRecords.add(item);
					failed = true;
					break;
				} 

				if ((count % 200) == 0 ) {
					System.out.println("TabyStudentHandler processing RECORD [" + count + "] time: " + IWTimestamp.getTimestampRightNow().toString());
				}
				
				item = null;
			}
      
			printFailedRecords();

			clock.stop();
			System.out.println("Number of records handled: " + (count - 1));
			System.out.println("Time to handleRecords: " + clock.getTime() + " ms  OR " + ((int)(clock.getTime()/1000)) + " s");

			//success commit changes
			if (!failed) {
				this.transaction.commit();
				System.out.println("Imported data committed to database");
			} else {
				this.transaction.rollback(); 
				System.out.println("Imported data rollbacked from database");
			}
			
			return !failed;
			
		} catch (Exception e) {
			e.printStackTrace();
			try {
				this.transaction.rollback();
			} catch (SystemException e2) {
				e2.printStackTrace();
			}

			return false;
		}
	}

	/*
	 * Processes one record 
	 */
	private boolean processRecord(String record, int count) throws RemoteException {
		if (count == 1) {
			// Skip header
			return true;
		}
//		userValues = file.getValuesFromRecordString2(record);
		this.userValues = getValuesFromRecordString2(record);
		boolean success = storeUserInfo(count);
		this.userValues = null;
				
		return success;
	}
	
	// Hack to fix multi-tab (three tabs in a row) bug
	private List getValuesFromRecordString2(String record) {
		String[] s = record.split("\t");
		List l = Arrays.asList(s);
		return l;
	}
  
	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#printFailedRecords() 
	 */
	public void printFailedRecords() {
		System.out.println("\n--------------------------------------------------\n");
		if (this.failedRecords.isEmpty()) {
			if (this.failedSchools.isEmpty()) {
				System.out.println("All records imported successfully.");
			}
		} else {
			System.out.println("Import failed for these records, please fix and import again:\n");
		}
  
		Iterator iter = this.failedRecords.iterator();

		while (iter.hasNext()) {
			System.out.println((String) iter.next());
		}

		if (!this.failedSchools.isEmpty()) {
			System.out.println("\nSchools missing from database or have different names:\n");
		}
		Collection cols = this.failedSchools.values();
		Iterator schools = cols.iterator();
		
		while (schools.hasNext()) {
			String name = (String) schools.next();
			System.out.println(name);
		}
		
		if (!this.errorLog.isEmpty()) {
			System.out.println("\nThe following error(s) logged:\n");
		}
		Iterator rowIter = this.errorLog.keySet().iterator();
		while (rowIter.hasNext()) {
			Integer row = (Integer) rowIter.next();
			String message = (String) this.errorLog.get(row);
			System.out.println("Row " + row + ": " + message);
		}
		
		System.out.println();
	}

	/**
	 * Stores one placement.
	 */
	protected boolean storeUserInfo(int rowNum) throws RemoteException {
		Integer row = new Integer(rowNum);
		User user = null;

//		String period = getUserProperty(this.COLUMN_PERIOD);  

		String schoolTypeName = getUserProperty(this.COLUMN_SCHOOL_TYPE);
		if (schoolTypeName == null ) {
			this.errorLog.put(row, "School type cannot be empty.");
			return false;
		}

		String schoolName = getUserProperty(this.COLUMN_SCHOOL_NAME);
		if (schoolName == null ) {
			log(row, "School name cannot be empty.");
			return false;
		}

		String personalId = getUserProperty(this.COLUMN_PERSONAL_ID);
		if (personalId == null) {
			log(row, "Personal ID cannot be empty.");
			return false;
		}
		personalId = "19" + personalId.replaceFirst("-", "");

		String studentFirstName = getUserProperty(this.COLUMN_STUDENT_FIRST_NAME);
		studentFirstName = studentFirstName == null ? "" : studentFirstName;

		String studentLastName = getUserProperty(this.COLUMN_STUDENT_LAST_NAME);
		studentLastName = studentLastName == null ? "" : studentLastName;
		
		String studentAddress = getUserProperty(this.COLUMN_STUDENT_ADDRESS);
		studentAddress = studentAddress == null ? "" : studentAddress;

		String studentZipCode = getUserProperty(this.COLUMN_STUDENT_ZIP_CODE);
		studentZipCode = studentZipCode == null ? "" : studentZipCode;

		String studentZipArea = getUserProperty(this.COLUMN_STUDENT_ZIP_AREA);
		studentZipArea = studentZipArea == null ? "" : studentZipArea;

		String homeCommuneCode = getUserProperty(this.COLUMN_HOME_COMMUNE);
		homeCommuneCode = homeCommuneCode == null ? "" : homeCommuneCode;

		String homeCountyCode = getUserProperty(this.COLUMN_HOME_COUNTY);
		homeCountyCode = homeCountyCode == null ? "" : homeCountyCode;
		if (homeCountyCode.length() == 1) {
			homeCountyCode = "0" + homeCountyCode;
		}
		
		homeCommuneCode = homeCountyCode + homeCommuneCode;
		
		String schoolYearName = getUserProperty(this.COLUMN_SCHOOL_YEAR);
		if (schoolYearName == null) {
			log(row, "School year cannot be empty.");
			return false;
		}
		if (schoolYearName.equals("0")) {
			schoolYearName = "F";
		}
		
		String schoolClass = getUserProperty(this.COLUMN_SCHOOL_CLASS);
		if (schoolClass == null) {
			log(row, "School Class cannot be empty.");
			return false;
		}

		String motherTongue = getUserProperty(this.COLUMN_MOTHER_TONGUE);
		motherTongue = motherTongue == null ? "" : motherTongue;

		School school = null;
		SchoolYear schoolYear = null;
		SchoolType schoolType = null; 

		// school type
		String typeKey = null;
		String schoolYearPrefix = "";
				
		if (schoolYearName.equals("F")) {
			typeKey = "sch_type.school_type_forskoleklass";
		} else if (schoolTypeName.equals("GR")) {
			typeKey = "sch_type.school_type_grundskola";
		} else {
			typeKey = "sch_type.school_type_sarskola";
			schoolYearPrefix = "S";
		}
		if (typeKey == null) {
			log(row, "School type: " + schoolTypeName + " not supported.");
			return false;
		}
		
		// caching of schooltype
		if(this.mapOfSchoolTypes.containsKey(typeKey)){
			schoolType = (SchoolType) this.mapOfSchoolTypes.get(typeKey);
		}
		else{
			try {
				schoolType = this.sTypeHome.findByTypeKey(typeKey);
				this.mapOfSchoolTypes.put(typeKey,schoolType);
			} catch (FinderException e) {
				log(row, "School type: " + schoolTypeName + " not found in database (key = " + typeKey + ").");
				return false;
			}
		}
			
		// user
		boolean isNewUser = false;
		boolean updateUser = false;
		try {
			user = this.biz.getUserHome().findByPersonalID(personalId);
		} catch (FinderException e) {
			System.out.println("User not found for PIN : " + personalId + " CREATING");
			
			try {
				user = this.biz.createSpecialCitizenByPersonalIDIfDoesNotExist(
						studentFirstName, 
						"",
						studentLastName,
						personalId,
						getGenderFromPin(personalId),
						getBirthDateFromPin(personalId));
				isNewUser = true;
			} catch (Exception e2) {
				e2.printStackTrace();
				return false;
			}
		}	

		if (user.getFirstName()!=null && user.getPersonalID()!=null && user.getFirstName().equals(user.getPersonalID())) {
			isNewUser = true;
			user.setFirstName(studentFirstName);
			user.setLastName(studentLastName);
		}
		
		if (isNewUser) {
			try {
				Integer communeId = null;
				if (!homeCommuneCode.equals("") && !homeCommuneCode.equals("0")) {
					// caching added
					if(this.mapOfCommunes.containsKey(homeCommuneCode)){
						communeId = (Integer) this.mapOfCommunes.get(homeCommuneCode);
					}
					else{
						Commune homeCommune = this.communeHome.findByCommuneCode(homeCommuneCode);
						communeId = (Integer) homeCommune.getPrimaryKey();
						this.mapOfCommunes.put(homeCommuneCode,communeId);
					}
				}
				this.biz.updateCitizenAddress(((Integer) user.getPrimaryKey()).intValue(), studentAddress, studentZipCode, studentZipArea, communeId);
			} catch (FinderException e) {
				log(row, "Commune not found: " + homeCommuneCode);
				return false;
			}
			updateUser = true;
		}

		if (motherTongue.length() > 0) {
			try {
				ICLanguage nativeLanguage = this.languageHome.findByDescription(motherTongue);
				user.setNativeLanguage(nativeLanguage);
				updateUser = true;		
			} catch (FinderException e) {
				log(row, "Language with code: " + motherTongue + " not found.");
				return false;
			}
		}
		
		if (updateUser) {
			user.store();
		}
				
		String description = user.getDescription();
		if (description == null) {
			description = "";	
		}
		if (!"secret".equals(description)) {
			if(this.mapOfSchools.containsKey(schoolName)){
				school = (School) this.mapOfSchools.get(schoolName);
			}
			else{
				try {
					//this can only work if there is only one school with this name. add more parameters for other areas
					school = this.sHome.findBySchoolName(schoolName);
					this.mapOfSchools.put(schoolName,school);
				} catch (FinderException e) {
					this.failedSchools.put(schoolName,schoolName);
					return false;
				}
			}

			String schoolKey = school.getPrimaryKey().toString();
			boolean hasSchoolType = false;
			try {
				Map types = null;
				if(this.mapOfSchoolRelatedTypes.containsKey(schoolKey)){
					types = (Map) this.mapOfSchoolRelatedTypes.get(schoolKey);
				}
				else{
					 types = this.schoolBiz.getSchoolRelatedSchoolTypes(school);
					 this.mapOfSchoolRelatedTypes.put(schoolKey,types);
				}
				/*
				Iterator schoolTypeIter = schoolBiz.getSchoolRelatedSchoolTypes(school).values().iterator();
				while (schoolTypeIter.hasNext()) {
					SchoolType st = (SchoolType) schoolTypeIter.next();
					
					if (st.getPrimaryKey().equals(schoolType.getPrimaryKey())) {
						hasSchoolType = true;
						break;
					}
				}*/
				hasSchoolType = types.containsKey(schoolType.getPrimaryKey());
			} catch (Exception e) {}
			
			if (!hasSchoolType) {
				log(row, "School type '" + schoolTypeName + "' not found in school: " + schoolName);
				return false;
			}
			
			if (schoolYearName.equals("0")) {
				schoolYearName = "F";
			} else {
				schoolYearName = schoolYearPrefix + schoolYearName; 
			}
			// caching added 
			if(this.mapOfSchoolYears.containsKey(schoolYearName)){
				schoolYear = (SchoolYear) this.mapOfSchoolYears.get(schoolYearName);
			}
			else{
				try {
					//school year	
					schoolYear = this.sYearHome.findByYearName(schoolYearName);
					this.mapOfSchoolYears.put(schoolYearName,schoolYear);
				} catch (FinderException e) {
					log(row, "School year not found: " + schoolYearName);
					return false;
				}
			}

			// caching added
			Map schoolYears = null;
			if(this.mapOfSchoolYearMaps.containsKey(schoolKey)){
				schoolYears = (Map) this.mapOfSchoolYearMaps.get(schoolKey);
			}
			else{
				schoolYears =this.schoolBiz.getSchoolRelatedSchoolYears(school);
				this.mapOfSchoolYearMaps.put(schoolKey,schoolYears);
			}
			
			Iterator schoolYearIter = schoolYears.values().iterator();
			boolean schoolYearFound = false;
			while (schoolYearIter.hasNext()) {
				SchoolYear sy = (SchoolYear) schoolYearIter.next();
				if (sy.getSchoolYearName().equals(schoolYearName)) {
					schoolYearFound = true;
					break;
				}
			}
			
			
			if (!schoolYearFound) {
				log(row, "School year '" + schoolYear + "' not found in school: " + schoolName);
				return false;
			}
										
			//school Class		
			SchoolClass sClass = null;
			
			try {	
				int schoolId = ((Integer) school.getPrimaryKey()).intValue();
				int seasonId = ((Integer) this.season.getPrimaryKey()).intValue();
				Collection c = this.sClassHome.findBySchoolAndSeason(schoolId, seasonId);
				Iterator iter = c.iterator();
				while (iter.hasNext()) {
					SchoolClass sc = (SchoolClass) iter.next();
					if (sc.getName().equals(schoolClass)) {
						try {
							sc.addSchoolYear(schoolYear);
						} catch (IDOAddRelationshipException e) { /* year already exists */ }
						sClass = sc;
						break;
					}
				}
				if (sClass == null) {
					throw new FinderException();
				}				
			} catch (Exception e) {
				System.out.println("School Class not found, creating '" + schoolClass + "' for school '" + schoolName + "'.");	
				int schoolId = ((Integer) school.getPrimaryKey()).intValue();
				int schoolTypeId = ((Integer) schoolType.getPrimaryKey()).intValue();
				int seasonId = ((Integer) this.season.getPrimaryKey()).intValue();
//				String[] schoolYearIds = {schoolYear.getPrimaryKey().toString()};
//				int schoolClassId = -1;
				try {
					sClass = this.sClassHome.create();
					sClass.setSchoolClassName(schoolClass);
					sClass.setSchoolId(schoolId);
					sClass.setSchoolTypeId(schoolTypeId);
					sClass.setSchoolSeasonId(seasonId);
					sClass.setValid(true);
					sClass.store();
					sClass.addSchoolYear(schoolYear);
				} catch (Exception e2) {}
//				sClass = schoolBiz.storeSchoolClass(schoolClassId, schoolClass, schoolId, schoolTypeId, seasonId, schoolYearIds, null);				
				if (sClass == null) {
					log(row, "Could not create school class: " + schoolClass);
					return false;
				}				
			}
			
			//school Class member
			SchoolClassMember member = null;
			boolean createNewPlacement = true;
			try {
//				Collection placements = sClassMemberHome.findAllByUserAndSeason(user, season);
				Collection placements = this.sClassMemberHome.findByStudent(user);
				if (placements != null) {
					Iterator oldPlacements = placements.iterator();
					while (oldPlacements.hasNext()) {
						SchoolClassMember placement = (SchoolClassMember) oldPlacements.next();
						SchoolType st = placement.getSchoolClass().getSchoolType();
						if (st != null && st.getPrimaryKey().equals(schoolType.getPrimaryKey())) {
							if (placement.getRemovedDate() == null) {
								int oldSchoolClassId = ((Integer) placement.getSchoolClass().getPrimaryKey()).intValue();
								int newSchoolClassId = ((Integer) sClass.getPrimaryKey()).intValue();
								if (oldSchoolClassId != newSchoolClassId) { 
									//IWTimestamp yesterday = new IWTimestamp();
									//yesterday.addDays(-1);
									IWTimestamp removedDateOldPlacement = new IWTimestamp(this.season.getSchoolSeasonStart());
									//placement.setRemovedDate(yesterday.getTimestamp());
									removedDateOldPlacement.addDays(-1);
									placement.setRemovedDate(removedDateOldPlacement.getTimestamp());
									placement.store();
									Collection c = this.resourceBiz.getResourcePlacementsByMemberId((Integer) placement.getPrimaryKey());
									Iterator resourceMemberIter = c.iterator();
									while (resourceMemberIter.hasNext()) {
										ResourceClassMember m = (ResourceClassMember) resourceMemberIter.next();
										//m.setEndDate(yesterday.getDate());
										m.setEndDate(removedDateOldPlacement.getDate());
										m.store();
									}
								} else {
									createNewPlacement = false;
									placement.setSchoolClassId(((Integer)sClass.getPrimaryKey()).intValue());
									placement.setSchoolYear(((Integer) schoolYear.getPrimaryKey()).intValue()); 
									placement.setSchoolTypeId(((Integer) schoolType.getPrimaryKey()).intValue()); 
									placement.store();
									member = placement;
								}
							}
						}
					}
				}
			} catch (FinderException f) {}

			if (createNewPlacement) {			
				member = this.schoolBiz.storeSchoolClassMember(sClass, user);
				if (member == null) {
					log(row, "School Class member could not be created for personal id: " + personalId);	
					return false;
				}
				//IWTimestamp registerDate = new IWTimestamp(REGISTER_DATE);
				IWTimestamp registerDate = new IWTimestamp(this.season.getSchoolSeasonStart());
				member.setRegisterDate(registerDate.getTimestamp());
				member.setRegistrationCreatedDate(IWTimestamp.getTimestampRightNow());
				member.setSchoolYear(((Integer) schoolYear.getPrimaryKey()).intValue()); 
				member.setSchoolTypeId(((Integer) schoolType.getPrimaryKey()).intValue()); 
				member.store();
			}
			
			int memberId = ((Integer) member.getPrimaryKey()).intValue();
			int resourceId = -1;
			
			boolean createMotherTongueResource = !motherTongue.equals("");
			resourceId = ((Integer) this.motherTongueResource.getPrimaryKey()).intValue();
			Collection rm = this.resourceBiz.getResourcePlacementsByMemberId((Integer) member.getPrimaryKey());
			Iterator rmIter = rm.iterator();
			while (rmIter.hasNext()) {
				ResourceClassMember m = (ResourceClassMember) rmIter.next();
				int mId = m.getResourceFK();
				if (mId == this.RESOURCE_ID_NATIVE_LANGUAGE) {
					if (!createMotherTongueResource) {
						IWTimestamp yesterday = new IWTimestamp();
						yesterday.addDays(-1);
						m.setEndDate(yesterday.getDate());
						m.store();
					} else {
						createMotherTongueResource = false;
					}
					break;
				}
			}
			if (createMotherTongueResource) {
				try {
					this.resourceBiz.createResourcePlacement(resourceId, memberId,new IWTimestamp(this.season.getSchoolSeasonStart()).toString());
				} catch (Exception e) {
					log(row, "Could not create resource placement (" + motherTongue + ") for personal id: " + personalId);
					return false;
				}
			}						
		} else {//remove secret market person from all schools this season
			System.out.println("TabyPlacementImportHandler Removing protected citizen from all classes (pin:" + user.getPersonalID() + ")");
			try{		
				Collection classMembers =  this.sClassMemberHome.findAllByUserAndSeason(user, this.season);
				
				Iterator oldClasses = classMembers.iterator();
				while (oldClasses.hasNext()) {
					SchoolClassMember temp = (SchoolClassMember) oldClasses.next();
					try {
						temp.remove();
					}
					catch (RemoveException e) {
						e.printStackTrace();
						log(row, "TabyStudentImportHandler failed removing protected citizen from all Classes (pin:"+user.getPersonalID()+")");
						return false;
					}		
				}
			} catch(FinderException f) {}
		}

		//finished with this user
		user = null;
		return true;
	}

	/*
	 * Returns the property for the specified column from the current record. 
	 */
	private String getUserProperty(int columnIndex){
		String value = null;
		
		if (this.userValues!=null) {
			try {
				value = (String) this.userValues.get(columnIndex);
			} catch (RuntimeException e) {
				return null;
			}
	 		//System.out.println("Index: "+columnIndex+" Value: "+value);
	 		if (this.file.getEmptyValueString().equals(value)) {
	 			return null;
	 		} else {
	 			return value;
	 		} 
		} else {
			return null;
  		} 
	}

	private IWTimestamp getBirthDateFromPin(String pin){
		//pin format = 190010221208 yyyymmddxxxx
		int dd = Integer.parseInt(pin.substring(6,8));
		int mm = Integer.parseInt(pin.substring(4,6));
		int yyyy = Integer.parseInt(pin.substring(0,4));
		IWTimestamp dob = new IWTimestamp(dd,mm,yyyy);
		return dob;
	}
	
	private Gender getGenderFromPin(String pin) {
		//pin format = 190010221208 second last number is the gender
		//even number = female
		//odd number = male
		try {
			GenderHome home = (GenderHome) this.getIDOHome(Gender.class);
			if (Integer.parseInt(pin.substring(10, 11)) % 2 == 0) {
				if (this.female == null) {
					this.female = home.getFemaleGender();
				}
				return this.female;
			} else {
				if (this.male == null) {
					this.male = home.getMaleGender();
				}
				return this.male;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#getFailedRecords()
	 */
	public void setImportFile(ImportFile file){
		this.file = file;
	}
		
	/**
	 * Not used
	 * @param rootGroup The rootGroup to set
	 */
	public void setRootGroup(Group rootGroup) {
	}

	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#getFailedRecords()
	 */
	public List getFailedRecords(){
		return this.failedRecords;	
	}

	private void log(Integer row, String message) {
		this.errorLog.put(row, message);
		System.out.println("Line " + row + ": " + message);
	}
}
