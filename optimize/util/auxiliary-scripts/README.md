# Auxiliary scripts

In this folder we store scripts that are helpful to us during development

## Export elastic search index contents to bulk format

The script 'convert_index_content_to_bulk_format.sh' allows you to export the contents of an elastic search index to the bulk format. This is useful for
writing upgrade tests.

To use it, change the parameters in the beginning of the file. For instructions on each of the parameters, please
read the comments in the file

## Refactor Repo

This scripts moves Optimize from the root module to a dedicated submodule called `optimize`
