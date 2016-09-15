Sources access service for MET API
=======================================

This module implements a service for reading metadata about sources. Currently,
only metadata from the STInfoSys database is available.

# Run

To be able to use the system, you will usually want to modify the
configuration files. For development purposes, you can instead create a file
`conf/development.conf` with the following entries:
```
db.sources.driver = org.postgresql.Driver
db.sources.url = "jdbc:postgresql://localhost:5432/stinfosys"
db.sources.username = <your-user-name>
db.sources.password = ""
db.sources.logStatements = true
play.http.router = sources.Routes
mail.override.address = "<your-email>"
play.evolutions.db.authorization.autoApply=true
auth.active=false
```

## Tests

To run the tests, do: `activator test`. To run tests with coverage report,
use: `activator coverage test coverageReport`.

## Running with Mock

To run the application with mock database connections, do: `activator run`

## Running in Test Production

To run the application in test production, you will need a working database
for the system to interact with.

A simple approach, on Ubuntu, is to install a Postgres server on localhost,
set it up for local connections (listen_addresses = '*' in postgresql.conf),
set it to trust local connections (in pg_hba.conf), and then create a local
database elements with `createdb stinfosys`. Given a backup dump of
stinfosys, you can then create a copy of the stinfosys database.

Alternatively, you can point your url at a test instance of stinfosys.

Once the database is set up, you can run test production using `activator testProd`.
