[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.jdbdt/jdbdt/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.jdbdt/jdbdt)
[![Build status](https://api.travis-ci.org/edrdo/jdbdt.png?branch=master)](https://travis-ci.org/edrdo/jdbdt)

# JDBDT 

JDBDT (Java Database Delta Testing) is a library for 
Java database application testing. 

Visit [http://jdbdt.org](http://jdbdt.org) for more information.

# License

JDBDT is open-source software under the terms of the 
[Eclipse Public License v 1.0](http://www.eclipse.org/legal/epl-v10.html).

# Download / installation

JDBDT is available at [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cjdbdt)  (click link on top of this page for the latest release).

Individual release artifacts are also available at [GitHub](https://github.com/edrdo/jdbdt/releases).

# Compilation 

Requirements:

* Maven 3.2 or higher
* Java 8 or higher

Commands: 

        git clone git@github.com:edrdo/jdbdt.git
        cd jdbdt
        mvn install

# Change Log

## 0.2 (next release)

* `DataSet`: `head` and `tail` methods respectively renamed to `first` and `last`.
* `ColumnFillerException` introduced to signal errors during column filler execution.
* Documentation improvements (site pages and Javadoc).

## 0.1

Initial release.


