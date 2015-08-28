# Sources access service for met-api

This implements a service for reading sources data, currently only
from a stinfosys database.

Document format is described in <https://phab.met.no/w/software/met_api/api_design/jsonld_schema/>.

------------

## Setup

This application only almost works out of the box. To make this work
for testing, you must create a file, conf/development.conf. This file
must at least contain the following keys:

  * db.stinfosys.url
  * db.stinfosys.username
  * db.stinfosys.password

See https://dokit.met.no/prosjekter/bora/kdvh-test for details on how
to set these options.

--------------

## Todo

### CGI params

- types - only SensorSystem currently allowed
- fields - not yet implemented
- validtime - ignored
- namespace - ignored

### JSON output

Header data not according to spec, but this is determined by the utils plugin

Error msgs not currently in JSON format

### Tests

Not implemented

### Mocking

Not implemented

### Logging

`logback.xml` autogenerated by Activator. Had to add `logger.xml` to make sql logging work.
