## Copying a Collection

A user with the Manager Role on a particular collection can now create a copy of that Collection.

## Data Sources for Collections

In order to keep your Collections focused on particular process and/or decision definitions the concept of Collection
Data Sources was introduced. It is now required to first define the Data Sources of a collection before definitions
can be selected in the report builder.

## Alerts only inside Collections

Alerts are now a part of a Collection. So you can only see and create Alerts inside a collection.
Existing alerts that were associated with a Report that didn't reside within a Collection are migrated to an
`Alert Archive` Collection that is created for each user that owned such alerts.

## Pending or Executing Flow Node Filter

Besides `was executed` and `was not executed` there is now the possibility to filter for `pending or executing` flow nodes
in a Process Report. 

## Process Instance Duration column for Raw Data Process Reports

In Raw Data Process Reports a new column was added that shows the duration of that particular process instance.
