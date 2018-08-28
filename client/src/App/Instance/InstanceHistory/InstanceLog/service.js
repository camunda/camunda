/**
 * gets the activityInstanceId corresponding to the given activityId
 * @param {*} activitiesDetails
 * @param {*} selectedActivityId
 */
export function getActivityInstanceIdByActivityId(
  activitiesDetails,
  activityId
) {
  return Object.keys(activitiesDetails).find(
    activityInstanceId =>
      activitiesDetails[activityInstanceId].activityId === activityId
  );
}
