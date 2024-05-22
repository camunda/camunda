
# Auxiliary scripts

In this folder we store scripts that are helpful to us during development

## Export elastic search index contents to bulk format

The script 'convert_index_content_to_bulk_format.sh' allows you to export the contents of an elastic search index to the bulk format. This is useful for 
writing upgrade tests.

To use it, change the parameters in the beginning of the file. For instructions on each of the parameters, please 
read the comments in the file

## Which test fails

This mini, poorly written, Java file helps you to figure out Jenkins time-outs. When Jenkins times out, one or more 
tests did not execute properly. By writing the Jenkins output to a text file, this script can parse it and tell you 
which test timed out.
To use it, unzip the file 'WhichTestFails.zip'. Then copy the Jenkins output from the web-browser and write it into a 
local file in your machine. 
Then add the path to the variable 'file' in the java source, then execute the script.