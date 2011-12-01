/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.generators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vivoweb.webapp.util.ModelUtils;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.XSD;

import edu.cornell.mannlib.vitro.webapp.beans.ObjectProperty;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.dao.VitroVocabulary;
import edu.cornell.mannlib.vitro.webapp.dao.WebappDaoFactory;
import edu.cornell.mannlib.vitro.webapp.dao.jena.QueryUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.DateTimeIntervalValidationVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.DateTimeWithPrecisionVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.FieldVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.preprocessors.RoleToActivityPredicatePreprocessor;
import edu.cornell.mannlib.vitro.webapp.utils.FrontEndEditingUtils.EditMode;
import edu.cornell.mannlib.vitro.webapp.utils.generators.EditModeUtils;
/**
 * Generates the edit configuration for adding a Role to a Person.  
  
  Stage one is selecting the type of the non-person thing 
  associated with the Role with the intention of reducing the 
  number of Individuals that the user has to select from.
  Stage two is selecting the non-person Individual to associate
  with the Role. 

  This is intended to create a set of statements like:

  ?person  core:hasResearchActivityRole ?newRole.
  ?newRole rdf:type core:ResearchActivityRole ;         
           roleToActivityPredicate ?someActivity .
  ?someActivity rdf:type core:ResearchActivity .
  ?someActivity rdfs:label "activity title" .
  
  
  
  Important: This form cannot be directly used as a custom form.  It has parameters that must be set.
  See addClinicalRoleToPerson.jsp for an example.
     
    roleToActivityPredicate and activityToRolePredicate are both dependent on the type of
    the activity itself. For a new statement, the predicate type is not known.
    For an existing statement, the predicate is known but may change based on the type of the activity newly selected.  
    
 
   bdc34:
   TODO: figure out what needs to be customized per role form, document it here in comments
   TODO: rewrite class as an abstract class with simple, documented, required methods to override
  
   AddRoleToPersonTwoStageGenerator is abstract, each subclass will need to configure:
   From the old JSP version:
   
   showRoleLabelField boolean
   roleType URI   
   roleToActivityPredicate URI
   activityToRolePredicate URI      
   roleActivityType_optionsType
   roleActivityType_objectClassURI
   roleActivityType_literalOptions
   
   For the new generator version:
    template
 *
 */
public abstract class AddRoleToPersonTwoStageGenerator extends BaseEditConfigurationGenerator implements EditConfigurationGenerator {
	
	private Log log = LogFactory.getLog(AddRoleToPersonTwoStageGenerator.class);
	
    /* ***** Methods that are REQUIRED to be implemented in subclasses ***** */

	/** Freemarker template to use */
	abstract String getTemplate();
	
	/** URI of type for the role context node */ 
    abstract String getRoleType();
    
    /** In the case of literal options, subclass generator will set the options to be returned */
    abstract HashMap<String, String> getRoleActivityTypeLiteralOptions();
    
    /**
     * Each subclass generator will return its own type of option here:    
     * whether literal hardcoded, based on class group, or subclasses of a specific class
     * The latter two will apparently lend some kind of uri to objectClassUri ? */
    abstract RoleActivityOptionTypes getRoleActivityTypeOptionsType();
    
    /** The URI of a Class to use with options if required. An option type like
     * CHILD_VCLASSES would reqire a role activity object class URI. */
    abstract String getRoleActivityTypeObjectClassUri(VitroRequest vreq);
    
    /** If true an input should be shown on the form for a 
     * label for the role context node 
     * TODO: move this to the FTL and have label optional. */
    abstract boolean isShowRoleLabelField();
    
    /** URI of predicate between role context node and activity */
    //Bdc34: not used anywhere? that's odd
//    abstract String getActivityToRolePredicate();            
		
    @Override
    public EditConfigurationVTwo getEditConfiguration(VitroRequest vreq, HttpSession session) {    	
        EditConfigurationVTwo editConfiguration = new EditConfigurationVTwo();    	    	
    	initProcessParameters(vreq, session, editConfiguration);
    	
        editConfiguration.setVarNameForSubject("person");               
        editConfiguration.setVarNameForPredicate("rolePredicate");      
        editConfiguration.setVarNameForObject("role");
        
    	// Required N3
    	editConfiguration.setN3Required(list(    	            	        
    	        N3_PREFIX + "\n" +
    	        "?person ?rolePredicate ?role .\n" +
    	        "?role a ?roleType .\n"+
    	        "?role " + getRoleToActivityPlaceholder() + " ?roleActivity .\n"+
    	        "?roleActivity " + getActivityToRolePlaceholder() + " ?role ."
    	));    
    	
    	// Optional N3 
    	editConfiguration.setN3Optional( list(
    	        "?role ?inverseRolePredicate ?person .",
    	        getN3ForActivityLabel(),
    	        getN3ForActivityType(),                              
    	        getN3RoleLabelAssertion(),
    	        getN3ForStart(),
    	        getN3ForEnd() ));	
        	    	        
    	editConfiguration.setNewResources( newResources(vreq) );
    	
    	//In scope
    	setUrisAndLiteralsInScope(editConfiguration, vreq);
    	
    	//on Form
    	setUrisAndLiteralsOnForm(editConfiguration, vreq);
    	    	
    	//Sparql queries
    	setSparqlQueries(editConfiguration, vreq);
    	
    	//set fields
    	setFields(editConfiguration, vreq, EditConfigurationUtils.getPredicateUri(vreq));
    	
    	//Form title and submit label now moved to edit configuration template
    	//TODO: check if edit configuration template correct place to set those or whether
    	//additional methods here should be used and reference instead, e.g. edit configuration template could call
    	//default obj property form.populateTemplate or some such method
    	//Select from existing also set within template itself
    	editConfiguration.setTemplate(getTemplate());
    	    	
    	//Add validator
        editConfiguration.addValidator(new DateTimeIntervalValidationVTwo("startField","endField") ); 
        //Add preprocessors
        addPreprocessors(editConfiguration, vreq.getWebappDaoFactory());
        //Adding additional data, specifically edit mode
        addFormSpecificData(editConfiguration, vreq);
        //prepare
        prepare(vreq, editConfiguration);
    	return editConfiguration;
    }    	

    private void initProcessParameters(VitroRequest vreq, HttpSession session, EditConfigurationVTwo editConfiguration) {    	        
    	editConfiguration.setFormUrl(EditConfigurationUtils.getFormUrlWithoutContext(vreq));    	
        editConfiguration.setEntityToReturnTo(EditConfigurationUtils.getSubjectUri(vreq));        
    }           
    
    /* N3 Required and Optional Generators as well as supporting methods */
    
	private String getN3ForActivityLabel() {
    	return "?roleActivity <" + RDFS.label.getURI() + "> ?activityLabel .";
    }
    
    private String getN3ForActivityType() {
    	return "?roleActivity a ?roleActivityType .";
    }
    
    private String getN3RoleLabelAssertion() {
    	return "?role <" + RDFS.label.getURI() + "> ?roleLabel .";
    }
	
	//Method b/c used in two locations, n3 optional and n3 assertions
	private List<String> getN3ForStart() {
		List<String> n3ForStart = new ArrayList<String>();
		n3ForStart.add("?role  <" + RoleToIntervalURI + "> ?intervalNode ." +     
			    "?intervalNode  <" + RDF.type.getURI() + "> <" + IntervalTypeURI + "> ." + 
			    "?intervalNode <" + IntervalToStartURI + "> ?startNode ." +     
			    "?startNode  <" + RDF.type.getURI() + "> <" + DateTimeValueTypeURI + "> ." + 
			    "?startNode  <" + DateTimeValueURI + "> ?startField-value ." + 
			    "?startNode  <" + DateTimePrecisionURI + "> ?startField-precision .");
		return n3ForStart;
	}
	
	private List<String> getN3ForEnd() {
		List<String> n3ForEnd = new ArrayList<String>();
		n3ForEnd.add("?role      <" + RoleToIntervalURI + "> ?intervalNode .  " +   
			    "?intervalNode  <" + RDF.type.getURI() + "> <" + IntervalTypeURI + "> ." + 
			    "?intervalNode <" + IntervalToEndURI + "> ?endNode ." + 
			    "?endNode  <" + RDF.type.getURI() + "> <" + DateTimeValueTypeURI + "> ." + 
			    "?endNode  <" + DateTimeValueURI + "> ?endField-value ." + 
			    "?endNode  <" + DateTimePrecisionURI+ "> ?endField-precision .");
		return n3ForEnd;		
	}
		
	/**  Get new resources	 */
	 private Map<String, String> newResources(VitroRequest vreq) {					
			String DEFAULT_NS_TOKEN=null; //null forces the default NS
			
			HashMap<String, String> newResources = new HashMap<String, String>();			
			newResources.put("role", DEFAULT_NS_TOKEN);
			newResources.put("roleActivity", DEFAULT_NS_TOKEN);
			newResources.put("intervalNode", DEFAULT_NS_TOKEN);
			newResources.put("startNode", DEFAULT_NS_TOKEN);
			newResources.put("endNode", DEFAULT_NS_TOKEN);
			return newResources;
		}
	
	/** Set URIS and Literals In Scope and on form and supporting methods	 */   
    private void setUrisAndLiteralsInScope(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
    	HashMap<String, List<String>> urisInScope = new HashMap<String, List<String>>();
    	
    	//Setting inverse role predicate
    	urisInScope.put("inverseRolePredicate", getInversePredicate(vreq));    
    	urisInScope.put("roleType", list( getRoleType() ) );
    	
    	//Uris in scope include subject, predicate, and object var
    	editConfiguration.setUrisInScope(urisInScope);    	
    	
    	//literals in scope empty initially, usually populated by code in prepare for update
    	//with existing values for variables
    }
    
    private List<String> getInversePredicate(VitroRequest vreq) {
		List<String> inversePredicateArray = new ArrayList<String>();
		ObjectProperty op = EditConfigurationUtils.getObjectProperty(vreq);
		if(op != null && op.getURIInverse() != null) {
			inversePredicateArray.add(op.getURIInverse());
		}
		return inversePredicateArray;
	}

    private void setUrisAndLiteralsOnForm(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
    	List<String> urisOnForm = new ArrayList<String>();    	
    	//add role activity and roleActivityType to uris on form
    	urisOnForm.add("roleActivity");
    	urisOnForm.add("roleActivityType");
    	//Also adding the predicates
    	//TODO: Check how to override this in case of default parameter? Just write hidden input to form?
    	urisOnForm.add("roleToActivityPredicate");
    	urisOnForm.add("activityToRolePredicate");
    	editConfiguration.setUrisOnform(urisOnForm);
    	
    	//activity label and role label are literals on form
    	List<String> literalsOnForm = new ArrayList<String>();
    	literalsOnForm.add("activityLabel");
    	literalsOnForm.add("roleLabel");
    	editConfiguration.setLiteralsOnForm(literalsOnForm);
    }    
    
    /** Set SPARQL Queries and supporting methods. */        
    private void setSparqlQueries(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {        
        //Queries for activity label, role label, start Field value, end Field value
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("activityLabel", getActivityLabelQuery(vreq));
        map.put("roleLabel", getRoleLabelQuery(vreq));
        map.put("startField-value", getExistingStartDateQuery(vreq));
        map.put("endField-value", getExistingEndDateQuery(vreq));
        
    	editConfiguration.setSparqlForExistingLiterals(map);
    	  
    	//Queries for role activity, activity type query, interval node, 
    	// start node, end node, start field precision, endfield precision
        map = new HashMap<String, String>();        
        map.put("roleActivity", getRoleActivityQuery(vreq));
        map.put("roleActivityType", getActivityTypeQuery(vreq));
        map.put("intervalNode", getIntervalNodeQuery(vreq));
        map.put("startNode", getStartNodeQuery(vreq));
        map.put("endNode", getEndNodeQuery(vreq));
        map.put("startField-precision", getStartPrecisionQuery(vreq));
        map.put("endField-precision", getEndPrecisionQuery(vreq));
        //Also need sparql queries for roleToActivityPredicate and activityToRolePredicate
        map.put("roleToActivityPredicate", getRoleToActivityPredicateQuery(vreq));
        map.put("activityToRolePredicate", getActivityToRolePredicateQuery(vreq));    	            	       
    	        
    	editConfiguration.setSparqlForExistingUris(map);
    }
        
    private String getActivityToRolePredicateQuery(VitroRequest vreq) {
    	String query = "SELECT ?existingActivityToRolePredicate \n " + 
		"WHERE { \n" +
	      "?roleActivity ?existingActivityToRolePredicate ?role .\n";
		//Get possible predicates
		List<String> addToQuery = new ArrayList<String>();
		List<String> predicates = getPossibleActivityToRolePredicates();
		for(String p:predicates) {
			addToQuery.add("(?existingActivityToRolePredicate=<" + p + ">)");
		}
		query += "FILTER (" + StringUtils.join(addToQuery, " || ") + ")\n";
		query += "}";
		return query;
	}

	private String getRoleToActivityPredicateQuery(VitroRequest vreq) {
		String query = "SELECT ?existingRoleToActivityPredicate \n " + 
		"WHERE { \n" +
	      "?role ?existingRoleToActivityPredicate ?roleActivity .\n";
		//Get possible predicates
		query += getFilterRoleToActivityPredicate("existingRoleToActivityPredicate");
		query += "\n}";
		return query;
	}

	private String getEndPrecisionQuery(VitroRequest vreq) {
		String query = "SELECT ?existingEndPrecision WHERE {\n" +
		      "?role <" + RoleToIntervalURI + "> ?intervalNode .\n" +
		    	      "?intervalNode <" + VitroVocabulary.RDF_TYPE + "> <" + IntervalTypeURI + "> .\n" +
		    	      "?intervalNode <" + IntervalToEndURI + "> ?endNode .\n" +
		    	      "?endNode <" + VitroVocabulary.RDF_TYPE + "> <" + DateTimeValueTypeURI + "> .  \n" +        
		    	      "?endNode <" + DateTimePrecisionURI + "> ?existingEndPrecision . }";
		return query;
	}

	private String getStartPrecisionQuery(VitroRequest vreq) {
		String query =  "SELECT ?existingStartPrecision WHERE {\n" +
	      "?role <" + RoleToIntervalURI + "> ?intervalNode .\n" +
	      "?intervalNode <" + VitroVocabulary.RDF_TYPE + "> <" + IntervalTypeURI + "> .\n" +
	      "?intervalNode <" + IntervalToStartURI + "> ?startNode .\n" +
	      "?startNode <" + VitroVocabulary.RDF_TYPE + "> <" + DateTimeValueTypeURI + "> .  \n" +        
	      "?startNode <" + DateTimePrecisionURI + "> ?existingStartPrecision . }";
		return query;
	}

	private String getEndNodeQuery(VitroRequest vreq) {
		String query =  "SELECT ?existingEndNode WHERE {\n"+
		      "?role <" + RoleToIntervalURI + "> ?intervalNode .\n"+
		      "?intervalNode <" + VitroVocabulary.RDF_TYPE + "> <" + IntervalTypeURI + "> .\n"+
		      "?intervalNode <" + IntervalToEndURI + "> ?existingEndNode . \n"+
		      "?existingEndNode <" + VitroVocabulary.RDF_TYPE + "> <" + DateTimeValueTypeURI + "> .}\n";      
		return query;
	}

	private String getStartNodeQuery(VitroRequest vreq) {
		String query =  "SELECT ?existingStartNode WHERE {\n"+ 
		      "?role <" + RoleToIntervalURI + "> ?intervalNode .\n"+ 
		      "?intervalNode <" + VitroVocabulary.RDF_TYPE + "> <" + IntervalTypeURI + "> .\n"+ 
		      "?intervalNode <" + IntervalToStartURI + "> ?existingStartNode . \n"+ 
		      "?existingStartNode <" + VitroVocabulary.RDF_TYPE + "> <" + DateTimeValueTypeURI + "> .}";      
		return query;
	}

	private String getIntervalNodeQuery(VitroRequest vreq) {
		String query =  "SELECT ?existingIntervalNode WHERE { \n" + 
	          "?role <" + RoleToIntervalURI + "> ?existingIntervalNode . \n" + 
	          " ?existingIntervalNode <" + VitroVocabulary.RDF_TYPE + "> <" + IntervalTypeURI + "> . }\n";
		return query;
	}

	
	/*
	 * The activity type query results must be limited to the values in the activity type select element. 
	 * Sometimes the query returns a superclass such as owl:Thing instead.
	 * Make use of vitro:mostSpecificType so that, for example, an individual is both a 
	 * core:InvitedTalk and a core:Presentation, core:InvitedTalk is selected.
	 * vitro:mostSpecificType alone may not suffice, since it does not guarantee that the value returned
	 * is in the select list.
	 * We could still have problems if the value from the select list is not a vitro:mostSpecificType, 
	 * but that is unlikely.
	 */
	//This method had some code already setup in the jsp file
	private String getActivityTypeQuery(VitroRequest vreq) {
		String activityTypeQuery = null;

		//roleActivityType_optionsType: This gets you whether this is a literal
		//
		RoleActivityOptionTypes optionsType = getRoleActivityTypeOptionsType();

	    // Note that this value is overloaded to specify either object class uri or classgroup uri
	    String objectClassUri = getRoleActivityTypeObjectClassUri(vreq);
	    
	    if (StringUtils.isNotBlank(objectClassUri)) { 
	        log.debug("objectClassUri = " + objectClassUri);
	        
			if (RoleActivityOptionTypes.VCLASSGROUP.equals(optionsType)) {
			    activityTypeQuery = getClassgroupActivityTypeQuery(vreq);
			    activityTypeQuery = QueryUtils.subUriForQueryVar(activityTypeQuery, "classgroup", objectClassUri);    	
			    
			} else if (RoleActivityOptionTypes.CHILD_VCLASSES.equals(optionsType)) { 
			    activityTypeQuery = getSubclassActivityTypeQuery(vreq);
			    activityTypeQuery = QueryUtils.subUriForQueryVar(activityTypeQuery, "objectClassUri", objectClassUri); 
			    
			} else {
			    activityTypeQuery = getDefaultActivityTypeQuery(vreq);  
			}
			
		// Select options are hardcoded
		} else if (RoleActivityOptionTypes.HARDCODED_LITERALS.equals(optionsType)) { 	    
		  
			//literal options
	        HashMap<String, String> typeLiteralOptions = getRoleActivityTypeLiteralOptions();
	        if (typeLiteralOptions.size() > 0) {           
	            try {
	                List<String> typeUris = new ArrayList<String>();
	                Set<String> optionUris = typeLiteralOptions.keySet();
	                for(String uri: optionUris) {
	                	if(!uri.isEmpty()) {
	                		typeUris.add("(?existingActivityType = <" + uri + ">)");
	                	}
	                }
	                String typeFilters = "FILTER (" + StringUtils.join(typeUris, "||") + ")";
	                String defaultActivityTypeQuery = getDefaultActivityTypeQuery(vreq);
	                activityTypeQuery = defaultActivityTypeQuery.replaceAll("}$", "") + typeFilters + "}";
	            } catch (Exception e) {
	                activityTypeQuery = getDefaultActivityTypeQuery(vreq);
	            }

		    } else { 
		        activityTypeQuery = getDefaultActivityTypeQuery(vreq);	    
		    } 

		} else {
		    activityTypeQuery = getDefaultActivityTypeQuery(vreq);   
		}

	    //The replacement of activity type query's predicate was only relevant when we actually
	    //know which predicate is definitely being used here
	    //Here we have multiple values possible for predicate so the original 
	    //Replacement should only happen when we have an actual predicate
	    
		String replaceRoleToActivityPredicate = getRoleToActivityPredicate(vreq);
		activityTypeQuery = QueryUtils.replaceQueryVar(activityTypeQuery, "predicate", getRoleToActivityPlaceholderName());
		log.debug("Activity type query: " + activityTypeQuery);
		
	    return activityTypeQuery;
	}

	
	private String getDefaultActivityTypeQuery(VitroRequest vreq) {
		String query =   "PREFIX core: <" + VIVO_NS + ">\n" +
	    "PREFIX vitro: <" + VitroVocabulary.vitroURI + "> \n" +
	    "SELECT ?existingActivityType WHERE { \n" +
	    "    ?role ?predicate ?existingActivity . \n" +
	    "    ?existingActivity vitro:mostSpecificType ?existingActivityType . \n";
		query += getFilterRoleToActivityPredicate("predicate");
	    query+= "}"; 
		return query;
	}

	private String getSubclassActivityTypeQuery(VitroRequest vreq) {
		String query = "PREFIX core: <" + VIVO_NS + ">\n" +
	    "PREFIX rdfs: <" + VitroVocabulary.RDFS + ">\n" +
	    "PREFIX vitro: <" + VitroVocabulary.vitroURI + "> \n" +
	    "SELECT ?existingActivityType WHERE {\n" +
	    "    ?role ?predicate ?existingActivity . \n" +
	    "    ?existingActivity vitro:mostSpecificType ?existingActivityType . \n" +
	    "    ?existingActivityType rdfs:subClassOf ?objectClassUri . \n";
		query += getFilterRoleToActivityPredicate("predicate");
	    query+= "}"; 
		return query;
	}

	private String getClassgroupActivityTypeQuery(VitroRequest vreq) {
		String query = "PREFIX core: <" + VIVO_NS + ">\n" +
	    "PREFIX vitro: <" + VitroVocabulary.vitroURI + "> \n" +
	    "SELECT ?existingActivityType WHERE { \n" +
	    "    ?role ?predicate ?existingActivity . \n" +
	    "    ?existingActivity vitro:mostSpecificType ?existingActivityType . \n" +
	    "    ?existingActivityType vitro:inClassGroup ?classgroup . \n";
		query += getFilterRoleToActivityPredicate("predicate");
	    query+= "}"; 
		return query;
	}


	private String getRoleActivityQuery(VitroRequest vreq) {
		//If role to activity predicate is the default query, then we need to replace with a union
		//of both realizedIn and the other
		String query =  "PREFIX core: <" + VIVO_NS + ">"; 
		
		//Portion below for multiple possible predicates
		List<String> predicates = getPossibleRoleToActivityPredicates();
		List<String> addToQuery = new ArrayList<String>();
		query += "SELECT ?existingActivity WHERE { \n" + 
		" ?role ?predicate ?existingActivity . \n ";	
		query += getFilterRoleToActivityPredicate("predicate");
		query += "}";
		return query;
	}
    
    private String getExistingEndDateQuery(VitroRequest vreq) {
    	String query = " SELECT ?existingEndDate WHERE {\n" + 
    		"?role <" + RoleToIntervalURI + "> ?intervalNode .\n" + 
    		"?intervalNode <" + VitroVocabulary.RDF_TYPE + "> <" + IntervalTypeURI + "> .\n" + 
    		"?intervalNode <" + IntervalToEndURI + "> ?endNode .\n" + 
    		"?endNode <" + VitroVocabulary.RDF_TYPE + "> <" + DateTimeValueTypeURI + "> .\n" + 
    		"?endNode <" + DateTimeValueURI + "> ?existingEndDate . }";
    	return query;
	}

	private String getExistingStartDateQuery(VitroRequest vreq) {
		String query = "SELECT ?existingDateStart WHERE {\n" + 
	     "?role <" + RoleToIntervalURI + "> ?intervalNode .\n" + 
	     "?intervalNode <" + VitroVocabulary.RDF_TYPE + "> <" + IntervalTypeURI + "> .\n" + 
	     "?intervalNode <" +  IntervalToStartURI+ "> ?startNode .\n" + 
	     "?startNode <" + VitroVocabulary.RDF_TYPE + "> <" + DateTimeValueTypeURI + "> .\n" + 
	     "?startNode <" +  DateTimeValueURI + "> ?existingDateStart . }";

	return query;
	}

	private String getRoleLabelQuery(VitroRequest vreq) {
		String query = "SELECT ?existingRoleLabel WHERE { \n" +
				"?role  <" + VitroVocabulary.LABEL + "> ?existingRoleLabel . }";
		return query;
	}

	private String getActivityLabelQuery(VitroRequest vreq) {
		String query =  "PREFIX core: <" + VIVO_NS + ">" + 
		"PREFIX rdfs: <" + RDFS.getURI() + "> \n";

		query +=  "SELECT ?existingTitle WHERE { \n" + 
		"?role ?predicate ?existingActivity . \n" +		
		"?existingActivity rdfs:label ?existingTitle . \n";
		query += getFilterRoleToActivityPredicate("predicate");
    	query += "}"; 
		return query;
	}

	/**
	 * 
	 * Set Fields and supporting methods
	 */
	
	private void setFields(EditConfigurationVTwo editConfiguration, VitroRequest vreq, String predicateUri) {
    	Map<String, FieldVTwo> fields = new HashMap<String, FieldVTwo>();
    	//Multiple fields
    	getActivityLabelField(editConfiguration, vreq, fields);
    	getRoleActivityTypeField(editConfiguration, vreq, fields);
    	getRoleActivityField(editConfiguration, vreq, fields);
    	getRoleLabelField(editConfiguration, vreq, fields);
    	getStartField(editConfiguration, vreq, fields);
    	getEndField(editConfiguration, vreq, fields);
    	//These fields are for the predicates that will be set later
    	//TODO: Do these only if not using a parameter for the predicate?
    	getRoleToActivityPredicateField(editConfiguration, vreq, fields);
    	getActivityToRolePredicateField(editConfiguration, vreq, fields);
    	editConfiguration.setFields(fields);
    }
    
	//This is a literal technically?
	private void getActivityToRolePredicateField(
			EditConfigurationVTwo editConfiguration, VitroRequest vreq,
			Map<String, FieldVTwo> fields) {
		String fieldName = "activityToRolePredicate";
		//get range data type uri and range language
		String stringDatatypeUri = XSD.xstring.toString();
		
		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);    	
    	//queryForExisting is not being used anywhere in Field
    	
    	//Not really interested in validators here
    	List<String> validators = new ArrayList<String>();
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	
    	field.setOptionsType("UNDEFINED");
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(null);
    	field.setRangeDatatypeUri(null);
    	
    	field.setLiteralOptions(new ArrayList<List<String>>());    	
    	fields.put(field.getName(), field);			
	}

	private void getRoleToActivityPredicateField(
			EditConfigurationVTwo editConfiguration, VitroRequest vreq,
			Map<String, FieldVTwo> fields) {
		String fieldName = "roleToActivityPredicate";
		//get range data type uri and range language
		String stringDatatypeUri = XSD.xstring.toString();
		
		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);    	
    	//queryForExisting is not being used anywhere in Field
    	
    	//Not really interested in validators here
    	List<String> validators = new ArrayList<String>();
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	
    	field.setOptionsType("UNDEFINED");
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(null);
    	field.setRangeDatatypeUri(null);
    	
    	field.setLiteralOptions(new ArrayList<List<String>>());    	
    
    	fields.put(field.getName(), field);			
	}

	//Label of "right side" of role, i.e. label for role roleIn Activity
	private void getActivityLabelField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq, Map<String, FieldVTwo> fields) {
		String fieldName = "activityLabel";
		//get range data type uri and range language
		String stringDatatypeUri = XSD.xstring.toString();
		
		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);    	
    	//queryForExisting is not being used anywhere in Field    	
    	
    	List<String> validators = new ArrayList<String>();
    	//If add mode or repair, etc. need to add label required validator
    	if(isAddMode(vreq) || isRepairMode(vreq)) {
    		validators.add("nonempty");
    	}
    	validators.add("datatype:" + stringDatatypeUri);
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	
    	field.setOptionsType("UNDEFINED");
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(null);
    	field.setRangeDatatypeUri(stringDatatypeUri);
    	    	
    	field.setLiteralOptions(new ArrayList<List<String>>());
    	    	
    	fields.put(field.getName(), field);	
	}
	
	//type of "right side" of role, i.e. type of activity from role roleIn activity
	private void getRoleActivityTypeField(
			EditConfigurationVTwo editConfiguration, VitroRequest vreq,
			Map<String, FieldVTwo> fields) {
		String fieldName = "roleActivityType";
		//get range data type uri and range language
		
		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);    	
    	
    	List<String> validators = new ArrayList<String>();
    	if(isAddMode(vreq) || isRepairMode(vreq)) {
    		validators.add("nonempty");
    	}
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	//TODO: Check if this is correct
    	field.setOptionsType(getRoleActivityTypeOptionsType().toString());
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(getRoleActivityTypeObjectClassUri(vreq));
    	field.setRangeDatatypeUri(null);
    	
    	
    	HashMap<String, String> literalOptionsMap = getRoleActivityTypeLiteralOptions();
    	List<List<String>> fieldLiteralOptions = new ArrayList<List<String>>();
    	Set<String> optionUris = literalOptionsMap.keySet();
    	for(String optionUri: optionUris) {
    		List<String> uriLabelArray = new ArrayList<String>();
    		uriLabelArray.add(optionUri);
    		uriLabelArray.add(literalOptionsMap.get(optionUri));
    		fieldLiteralOptions.add(uriLabelArray);
    	}
    	field.setLiteralOptions(fieldLiteralOptions);
    	
    	fields.put(field.getName(), field);
		
	}    
	
	//Assuming URI for activity for role?
	private void getRoleActivityField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq, Map<String, FieldVTwo> fields) {
		String fieldName = "roleActivity";
		//get range data type uri and range language
		
		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);    	
    	
    	List<String> validators = new ArrayList<String>();
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	
    	field.setOptionsType("UNDEFINED");
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(null);
    	field.setRangeDatatypeUri(null);
    	//empty
    	field.setLiteralOptions(new ArrayList<List<String>>());
    	
    	fields.put(field.getName(), field);	
	}
	
	private void getRoleLabelField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq, Map<String, FieldVTwo> fields) {
		String fieldName = "roleLabel";
		String stringDatatypeUri = XSD.xstring.toString();

		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);  
    	
    	List<String> validators = new ArrayList<String>();
    	validators.add("datatype:" + stringDatatypeUri);
    	if(isShowRoleLabelField()) {
    		validators.add("nonempty");
    	}
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	
    	field.setOptionsType("UNDEFINED");
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(null);
    	field.setRangeDatatypeUri(stringDatatypeUri);
    	//empty
    	field.setLiteralOptions(new ArrayList<List<String>>());
    	    	
    	fields.put(field.getName(), field);		
	}	

	private void getStartField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq, Map<String, FieldVTwo> fields) {
		String fieldName = "startField";

		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);    	    
    	
    	List<String> validators = new ArrayList<String>();
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	
    	field.setOptionsType("UNDEFINED");
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(null);
    	field.setRangeDatatypeUri(null);
    	//empty
    	field.setLiteralOptions(new ArrayList<List<String>>());
    	    	
    	//This logic was originally after edit configuration object created from json in original jsp
    	field.setEditElement(
                new DateTimeWithPrecisionVTwo(field, 
                        VitroVocabulary.Precision.YEAR.uri(),
                        VitroVocabulary.Precision.NONE.uri()));   
    	
    	fields.put(field.getName(), field);		
	}

	private void getEndField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq, Map<String, FieldVTwo> fields) {
		String fieldName = "endField";

		FieldVTwo field = new FieldVTwo();
    	field.setName(fieldName);    	
    	
    	List<String> validators = new ArrayList<String>();
    	field.setValidators(validators);
    	
    	//subjectUri and subjectClassUri are not being used in Field
    	
    	field.setOptionsType("UNDEFINED");
    	//why isn't predicate uri set for data properties?
    	field.setPredicateUri(null);
    	field.setObjectClassUri(null);
    	field.setRangeDatatypeUri(null);
    	//empty
    	field.setLiteralOptions(new ArrayList<List<String>>());
    	
    	//Set edit element
    	 field.setEditElement(
                 new DateTimeWithPrecisionVTwo(field, 
                         VitroVocabulary.Precision.YEAR.uri(),
                         VitroVocabulary.Precision.NONE.uri()));
    	
    	fields.put(field.getName(), field);		
	}
       	
   private void addPreprocessors(EditConfigurationVTwo editConfiguration, WebappDaoFactory wadf) {
	   //Add preprocessor that will replace the role to activity predicate and inverse
	   //with correct properties based on the activity type
	   editConfiguration.addEditSubmissionPreprocessor(
			   new RoleToActivityPredicatePreprocessor(editConfiguration, wadf));
	   
	}
   
    //This has a default value, but note that even that will not be used
    //in the update with realized in or contributes to
    //Overridden when need be in subclassed generator
	//Also note that for now we're going to actually going to return a 
	//placeholder value by default	
	public String getRoleToActivityPredicate(VitroRequest vreq) {
	  //TODO: <uri> and ?placeholder are incompatible
		return getRoleToActivityPlaceholder();
	}
	//Ensure when overwritten that this includes the <> b/c otherwise the query won't work

	//Some values will have a default value

	public List<String> getPossibleRoleToActivityPredicates() {
		return ModelUtils.getPossiblePropertiesForRole();
	}
	
	public List<String> getPossibleActivityToRolePredicates() {
		return ModelUtils.getPossibleInversePropertiesForRole();
	}
	
	/* Methods that check edit mode	 */	
	public EditMode getEditMode(VitroRequest vreq) {
		List<String> roleToGrantPredicates = getPossibleRoleToActivityPredicates();
		return EditModeUtils.getEditMode(vreq, roleToGrantPredicates);
	}

	private boolean isAddMode(VitroRequest vreq) {
    	return EditModeUtils.isAddMode(getEditMode(vreq));
    }
    
    private boolean isEditMode(VitroRequest vreq) {
    	return EditModeUtils.isEditMode(getEditMode(vreq));
    }
    
    private boolean isRepairMode(VitroRequest vreq) {
    	return EditModeUtils.isRepairMode(getEditMode(vreq));
    }
    
	/* URIS for various predicates */
	private final String VIVO_NS="http://vivoweb.org/ontology/core#";
	
	private final String RoleToIntervalURI =  VIVO_NS + "dateTimeInterval";	
	private final String IntervalTypeURI =    VIVO_NS + "DateTimeInterval";		
	private final String IntervalToStartURI = VIVO_NS + "start";		
	private final String IntervalToEndURI =   VIVO_NS + "end";		
	private final String StartYearPredURI =   VIVO_NS + "startYear";	
	private final String EndYearPredURI =     VIVO_NS + "endYear";
	private final String DateTimeValueTypeURI=VIVO_NS + "DateTimeValue";	
	private final String DateTimePrecisionURI=VIVO_NS + "dateTimePrecision";	
	private final String DateTimeValueURI =   VIVO_NS + "dateTime";	
	
	//Form specific data
	public void addFormSpecificData(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
		HashMap<String, Object> formSpecificData = new HashMap<String, Object>();
		formSpecificData.put("editMode", getEditMode(vreq).name().toLowerCase());
		//Fields that will need select lists generated
		//Store field names
		List<String> objectSelect = new ArrayList<String>();
		objectSelect.add("roleActivityType");
		//TODO: Check if this is the proper way to do this?
		formSpecificData.put("objectSelect", objectSelect);
		//Also put in show role label field
		formSpecificData.put("showRoleLabelField", isShowRoleLabelField());
		//Put in the fact that we require field
		editConfiguration.setFormSpecificData(formSpecificData);
	}
		
	public String getFilterRoleToActivityPredicate(String predicateVar) {
		String addFilter = "FILTER (";
		List<String> predicates = getPossibleRoleToActivityPredicates();
		List<String> filterPortions = new ArrayList<String>();
		for(String p: predicates) {
			filterPortions.add("(?" + predicateVar + "=<" + p + ">)");
		}
		addFilter += StringUtils.join(filterPortions, " || ");
		addFilter += ")";
		return addFilter;
	}
	
	private String getRoleToActivityPlaceholder() {
		return "?" + getRoleToActivityPlaceholderName();
	}
	
	private String getRoleToActivityPlaceholderName() {
		return "roleToActivityPredicate";
	}
	
	
	private String getActivityToRolePlaceholder() {
		return "?activityToRolePredicate";
	}

	//Types of options to populate drop-down for types for the "right side" of the role
    public static enum RoleActivityOptionTypes {
        VCLASSGROUP,
        CHILD_VCLASSES,
        HARDCODED_LITERALS
    };
    
	private final String N3_PREFIX = "@prefix core: <http://vivoweb.org/ontology/core#> .";
}
