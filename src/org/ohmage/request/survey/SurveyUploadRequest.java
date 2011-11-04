package org.ohmage.request.survey;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.domain.campaign.Campaign;
import org.ohmage.domain.campaign.SurveyResponse;
import org.ohmage.exception.ServiceException;
import org.ohmage.exception.ValidationException;
import org.ohmage.request.InputKeys;
import org.ohmage.request.UserRequest;
import org.ohmage.service.CampaignServices;
import org.ohmage.service.SurveyResponseServices;
import org.ohmage.service.UserCampaignServices;
import org.ohmage.validator.CampaignValidators;
import org.ohmage.validator.DateValidators;
import org.ohmage.validator.ImageValidators;

/**
 * <p>Stores a survey and its associated images (if any are present in the payload)</p>
 * <table border="1">
 *   <tr>
 *     <td>Parameter Name</td>
 *     <td>Description</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#CLIENT}</td>
 *     <td>A string describing the client that is making this request.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#USERNAME}</td>
 *     <td>The username of the uploader.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#PASSWORD}</td>
 *     <td>The password for the associated username/</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#CAMPAIGN_URN}</td>
 *     <td>The campaign URN for the survey(s) being uploaded.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#CAMPAIGN_CREATION_TIMESTAMP}</td>
 *     <td>The creation timestamp for the campaign. This parameter is used to
 *     ensure that the client's campaign is up-to-date.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>{@value org.ohmage.request.InputKeys#SURVEY}</td>
 *     <td>The survey data payload for the survey(s) being uploaded.</td>
 *     <td>true</td>
 *   </tr>
 *   <tr>
 *     <td>A UUID linking the binary image data to a UUID that must be present
 *      in the survey data payload. There can be many images attached to a
 *      survey upload.</td>
 *     <td></td>
 *     <td>true, only if the survey data payload contains image prompt responses</td>
 *   </tr>
 * </table>
 * 
 * @author Joshua Selsky
 */
public class SurveyUploadRequest extends UserRequest {
	private static final Logger LOGGER = Logger.getLogger(SurveyUploadRequest.class);
	
	// The campaign creation timestamp is stored as a String because it is 
	// never used in any kind of calculation.
	private final String campaignUrn;
	private final Date campaignCreationTimestamp;
	private List<JSONObject> jsonData;
	private final Map<String, BufferedImage> imageContentsMap;
	
	/**
	 * Creates a new image upload request.
	 * 
	 * @param httpRequest The HttpServletRequest with the parameters for this
	 * 					  request.
	 */
	public SurveyUploadRequest(HttpServletRequest httpRequest) {
		super(httpRequest, false);
		
		LOGGER.info("Creating a survey upload request.");

		String tCampaignUrn = null;
		Date tCampaignCreationTimestamp = null;
		List<JSONObject> tJsonData = null;
		Map<String, BufferedImage> tImageContentsMap = null;
		
		if(! isFailed()) {
			try {
				Map<String, String[]> parameters = getParameters();
				
				// Validate the campaign URN
				String[] t = parameters.get(InputKeys.CAMPAIGN_URN);
				if(t == null || t.length != 1) {
					setFailed(ErrorCode.CAMPAIGN_INVALID_ID, "campaign_urn is missing or there is more than one.");
					throw new ValidationException("campaign_urn is missing or there is more than one.");
				} else {
					tCampaignUrn = CampaignValidators.validateCampaignId(t[0]);
					
					if(tCampaignUrn == null) {
						setFailed(ErrorCode.CAMPAIGN_INVALID_ID, "The campaign ID is invalid.");
						throw new ValidationException("The campaign ID is invalid.");
					}
				}
				
				// Validate the campaign creation timestamp
				t = parameters.get(InputKeys.CAMPAIGN_CREATION_TIMESTAMP);
				if(t == null || t.length != 1) {
					setFailed(ErrorCode.SERVER_INVALID_TIMESTAMP, "campaign_creation_timestamp is missing or there is more than one");
					throw new ValidationException("campaign_creation_timestamp is missing or there is more than one");
				} 
				else {
					
					// Make sure it's a valid timestamp
					try {
						tCampaignCreationTimestamp = DateValidators.validateISO8601DateTime(t[0]);
					}
					catch(ValidationException e) {
						setFailed(ErrorCode.SERVER_INVALID_DATE, e.getMessage());
						throw e;
					}
				}
				
				t = parameters.get(InputKeys.SURVEYS);
				if(t == null || t.length != 1) {
					setFailed(ErrorCode.SURVEY_INVALID_RESPONSES, "No value found for 'surveys' parameter or multiple surveys parameters were found.");
					throw new ValidationException("No value found for 'surveys' parameter or multiple surveys parameters were found.");
				}
				else {
					try {
						tJsonData = CampaignValidators.validateUploadedJson(t[0]);
					}
					catch(IllegalArgumentException e) {
						setFailed(ErrorCode.SURVEY_INVALID_RESPONSES, "The survey responses could not be URL decoded.");
						throw new ValidationException("The survey responses could not be URL decoded.", e);
					}
				}
				
				// Retrieve and validate images
				List<String> imageIds = new ArrayList<String>();
				Collection<Part> parts = null;
				try {
					// FIXME - push to base class especially because of the ServletException that gets thrown
					parts = httpRequest.getParts();
					for(Part p : parts) {
						try {
							UUID.fromString(p.getName());
							imageIds.add(p.getName());
						}
						catch (IllegalArgumentException e) {
							// ignore because there may not be any UUIDs/images
						}
					}
				}
				catch(ServletException e) {
					LOGGER.error("cannot parse parts", e);
					setFailed();
					throw new ValidationException(e);
				}
				catch(IOException e) {
					LOGGER.error("cannot parse parts", e);
					setFailed();
					throw new ValidationException(e);
				}
				
				Set<String> stringSet = new HashSet<String>(imageIds);
				
				if(stringSet.size() != imageIds.size()) {
					setFailed(ErrorCode.IMAGE_INVALID_DATA, "a duplicate image key was detected in the multi-part upload");
					throw new ValidationException("a duplicate image key was detected in the multi-part upload");
				}

				tImageContentsMap = new HashMap<String, BufferedImage>();
				for(String imageId : imageIds) {
					BufferedImage bufferedImage = ImageValidators.validateImageContents(getMultipartValue(httpRequest, imageId));
					if(bufferedImage == null) {
						throw new ValidationException(ErrorCode.IMAGE_INVALID_DATA, "The image data is missing: " + imageId);
					}
					tImageContentsMap.put(imageId, bufferedImage);
					
					if(LOGGER.isDebugEnabled()) {
						LOGGER.debug("succesfully created a BufferedImage for key " + imageId);
					}
				}
				
			}
			catch(ValidationException e) {
				e.failRequest(this);
				LOGGER.info(e.toString());
			}
		}

		this.campaignUrn = tCampaignUrn;
		this.campaignCreationTimestamp = tCampaignCreationTimestamp;
		this.jsonData = tJsonData;
		this.imageContentsMap = tImageContentsMap;
	}

	/**
	 * Services the request.
	 */
	@Override
	public void service() {
		LOGGER.info("Servicing a survey upload request.");
		
		if(! authenticate(AllowNewAccount.NEW_ACCOUNT_DISALLOWED)) {
			return;
		}
		
		try {
			LOGGER.info("Verifying that the user is a participant in the campaign.");
			UserCampaignServices.instance().verifyUserCanUploadSurveyResponses(getUser().getUsername(), campaignUrn);
			
			LOGGER.info("Verifying that the campaign is running.");
			CampaignServices.instance().verifyCampaignIsRunning(campaignUrn);
			
			LOGGER.info("Verifying that the uploaded survey responses aren't out of date.");
			CampaignServices.instance().verifyCampaignIsUpToDate(campaignUrn, campaignCreationTimestamp);
			
			LOGGER.info("Generating the campaign object.");
			Campaign campaign = CampaignServices.instance().findCampaignConfiguration(campaignUrn);
			
			LOGGER.info("Verifying the uploaded data against the campaign.");
			List<SurveyResponse> surveyResponses = 
				CampaignServices.instance().getSurveyResponses(
						getUser().getUsername(), 
						getClient(),
						campaign, 
						jsonData);

			LOGGER.info("Validating that all photo prompt responses have their corresponding images attached.");
			SurveyResponseServices.instance().verifyImagesExistForPhotoPromptResponses(surveyResponses, imageContentsMap);
			
			LOGGER.info("Inserting the data into the database.");
			List<Integer> duplicateIndexList = SurveyResponseServices.instance().createSurveyResponses(getUser().getUsername(), getClient(), campaignUrn, surveyResponses, imageContentsMap);

			LOGGER.info("Found " + duplicateIndexList.size() + " duplicate survey uploads");
		}
		catch(ServiceException e) {
			e.failRequest(this);
			e.logException(LOGGER);
		}
	}

	/**
	 * Responds to the image upload request with success or a failure message
	 * that contains a failure code and failure text.
	 */
	@Override
	public void respond(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		LOGGER.info("Responding to the survey upload request.");
		
		super.respond(httpRequest, httpResponse, null);
	}
}