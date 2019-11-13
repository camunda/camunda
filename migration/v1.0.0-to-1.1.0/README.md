# Create backup of old indexes #
* Add for each index that should be backup a reindex request payload as json file in backup-index folder 

# Delete old indexes #
* Define a pattern that matches all the old indexes in DELETE request

# Create new templates and indexes #
* Add for each index and template that should be added a create index/template request payload as json file in create/index or create/template folder 

# Create pipelines for migration #
* Add for each index that should be converted a create pipeline request payload as json file in pipelines folder 

# Reindex from old to new schema #
* Add for each index that should be converted a reindex request payload as json file in reindex folder
** Make sure you give the appropriate pipeline name in reindex request