#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#
declare -A lookupTeamMedic
# @core-features-medic
coreFeaturesMedic="<!subteam^S08P2CU9V8W|core-features-medic>"
lookupTeamMedic["team-core-features"]=$coreFeaturesMedic
lookupTeamMedic["Core Features"]=$coreFeaturesMedic
lookupTeamMedic["CoreFeatures"]=$coreFeaturesMedic

# @data-layer-medic
dataLayerMedic="<!subteam^S08P2CSC06S|data-layer-medic>"
lookupTeamMedic["team-data-layer"]=$dataLayerMedic
lookupTeamMedic["Data Layer"]=$dataLayerMedic
lookupTeamMedic["DataLayer"]=$dataLayerMedic

# @identity-medic
identityMedic="<!subteam^S053MF48SSH|identity-medic>"
lookupTeamMedic["team-identity"]=$identityMedic
lookupTeamMedic["Identity"]=$identityMedic

# no current medic slack handle for this team
distributedSystemsMedic="Distributed Systems Medic"
lookupTeamMedic["team-distributed-systems"]=$distributedSystemsMedic
lookupTeamMedic["Distributed Systems"]=$distributedSystemsMedic
lookupTeamMedic["DistributedSystems"]=$distributedSystemsMedic

# failure in QA test
lookupTeamMedic["QA"]="QA Acceptance Test, requires investigation"

# catch all for tests without an assigned team
lookupTeamMedic["General"]="General Test, requires investigation"
