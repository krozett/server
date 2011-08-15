package org.ohmage.service;

import java.sql.Timestamp;
import java.util.Calendar;

import org.ohmage.dao.UserSurveyDaos;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.ServiceException;
import org.ohmage.request.Request;

/**
 * This class contains all of the services pertaining to reading and writing
 * user-survey information.
 * 
 * @author John Jenkins
 */
public class UserSurveyServices {
	private static final long MILLIS_IN_A_HOUR = 60 * 60 * 1000;
	private static final int HOURS_IN_A_DAY = 24;
	
	/**
	 * Default constructor. Private so that it cannot be instantiated.
	 */
	private UserSurveyServices() {}
	
	/**
	 * Retrieves the number of hours since the last uploaded survey was taken
	 * that is visible to the requesting user.
	 * 
	 * @param request The Request that is performing this service.
	 * 
	 * @param requestersUsername The username of the user that is making this
	 * 							 request.
	 * 
	 * @param usersUsername The username of the user that to which the data
	 * 						pertains.
	 * 
	 * @return The number of hours since the last time a survey was taken.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public static double getHoursSinceLastSurveyUplaod(Request request, String requestersUsername, String usersUsername) throws ServiceException {
		try {
			Timestamp lastUpload = UserSurveyDaos.getLastUploadForUser(requestersUsername, usersUsername);
			if(lastUpload == null) {
				return Double.MAX_VALUE;
			}
			else {
				long differenceInMillis = Calendar.getInstance().getTimeInMillis() - lastUpload.getTime();
				
				return new Double(differenceInMillis) / new Double(MILLIS_IN_A_HOUR);
			}
		}
		catch(DataAccessException e) {
			request.setFailed();
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the percentage of non-null location points to total location
	 * points for all surveys uploaded in the last 24 hours.
	 * 
	 * @param request The Request that is performing this service.
	 * 
	 * @param requestersUsername The username of the user that is requesting
	 * 							 this information.
	 * 
	 * @param usersUsername The username of the user to which the survey points
	 * 						belong.
	 * 
	 * @return Returns the percentage of non-null location values uploaded by
	 * 		   'usersUsername' over the last 24 hours. If there are no points,
	 * 		   -1.0 is returned.
	 * 
	 * @throws ServiceException Thrown if there is an error. 
	 */
	public static double getPercentageOfNonNullLocationsOverPastDay(Request request, String requestersUsername, String usersUsername) throws ServiceException {
		try {
			Double percentage = UserSurveyDaos.getPercentageOfNonNullSurveyLocations(requestersUsername, usersUsername, HOURS_IN_A_DAY);
			if(percentage == null) {
				return -1.0;
			}
			else {
				return percentage;
			}
		}
		catch(DataAccessException e) {
			request.setFailed();
			throw new ServiceException(e);
		}
	}
}
