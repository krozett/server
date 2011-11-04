package org.ohmage.query;

import java.util.List;

import org.ohmage.domain.Clazz;
import org.ohmage.domain.campaign.Campaign;
import org.ohmage.exception.DataAccessException;

/**
 * Interface to facilitate mocking concrete implementations for test cases. 
 * 
 * @author John Jenkins
 * @author Joshua Selsky
 */
public interface ICampaignClassQueries {

	/**
	 * Retrieves the list of unique identifiers for campaigns that are 
	 * associated with the class.
	 * 
	 * @param classId The unique identifier for the class.
	 * 
	 * @return A List of campaign identifiers for all of the campaigns 
	 * 		   associated with this class.
	 */
	List<String> getCampaignsAssociatedWithClass(String classId)
			throws DataAccessException;

	/**
	 * Retrieves the list of unique identifiers for all of the classes that are
	 * associated with the campaign.
	 * 
	 * @param campaignId The campaign's unique identifier.
	 * 
	 * @return A list of class IDs for all of the classes associated with this
	 * 		   campaign.
	 */
	List<String> getClassesAssociatedWithCampaign(String campaignId)
			throws DataAccessException;

	/**
	 * Retrieves the list of default campaign roles for a user in a class with
	 * the specified class role.
	 * 
	 * @param campaignId The campaign's unique identifier.
	 * 
	 * @param classId The class' unique identifier.
	 * 
	 * @param classRole The class role.
	 * 
	 * @return A, possibly empty but never null, list of campaign roles.
	 * 
	 * @throws DataAccessException Thrown if there is an error.
	 */
	List<Campaign.Role> getDefaultCampaignRolesForCampaignClass(
			String campaignId, String classId, Clazz.Role classRole)
			throws DataAccessException;

}