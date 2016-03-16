# Link Checker Maven Plugin

The `linkchecker-maven-plugin` is a Maven plugin to check links in a given HTML file. Non-URL links (i.e. local files)
will be taken for further link checking.

This plugin will crawl the links (`a`, `frame` and `img`) in local HTML files. It will validate all links to go out to
real files or web locations. It will stop there, and not crawl the whole Internet! It was originally designed to
validate the links between help documents in HTML, that pointed to each other, to local image files, and to some web
locations.

This plugin does not require a project, rather a HTML file to start from (`startFile`), and it does not bind to a
default lifecycle phase. It has a single goal: `check`

Here is a usage example of this plugin:

    <plugin>
        <groupId>com.jamasoftware.maven.plugin</groupId>
        <artifactId>linkchecker-maven-plugin</artifactId>
        <executions>
            <execution>
                <phase>test</phase>
                <goals>
                    <goal>check</goal>
                </goals>
                <configuration>
                    <startFile>${project.basedir}/src/output/Webhelp/index.html</startFile>
                </configuration>
            </execution>
        </executions>
    </plugin>

This plugin supports the following configuration parameters:

| Parameter | Type | Required | Default | Description | User property |
|---|---|---|---|---|---|
| `startFile` | `File` | true |  | The file to start from. Links from the file will be checked. Non-URL links (i.e. local files) will be taken for further link checking (feels like recursion) | `linkchecker.startFile` |
| `defaultFile` | `String` | false | `index.html` | The file name to be used as the default, in case a (non-URL) link points to a folder. It's what happens on a web server: requests for `http://foo/bar` will serve you (typically) `http://foo/bar/index.html` | `linkchecker.defaultFile` |
| `failOnLocalHost` | `boolean` | false | `true` | Should this plugin make your build fail if it encounters links to `localhost`. Typically, depending on something local to the build would hamper the portability of the build | `linkchecker.failOnLocalHost` |
| `failOnBadUrls` | `boolean` | false | `false` | Should this plugin make your build fail if it encounters bad URLs. This is not the default, in appreciation of the fact that (non-local) URLs are out of our control. Typically, validating (non-local) URLs would hamper the reproducibility of the build | `linkchecker.failOnBadUrls` |
| `reportOnly` | `boolean` | false | `false` | Should this plugin make your build fail altogether, or only report its findings. | `linkchecker.reportOnly` |
| `skip` | `boolean` | false | `false` | Skip this plugin execution. | `linkchecker.skip` |

## License

This project is licensed under [the Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

## About Jama

Jama is software for better, faster requirements definition, management, verification and validation, from inception to
production. Our vision is of Modern Product Delivery. Building new products shouldn’t be frustrating and wasteful. It
ought to be enlightening and profitable. We make possible the impossible products of the future. Find more information
on [our web site](http://www.jamasoftware.com/). Jama Software is a fast-growing company, and we are [hiring]
(http://www.jamasoftware.com/company/careers/).
