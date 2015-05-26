package com.jamasoftware.maven.plugin.linkchecker.mojo;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import org.apache.maven.plugins.annotations.Parameter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Check links.
 */
@Mojo(name = "check", requiresProject = false)
public class CheckMojo extends AbstractMojo {

    /**
     * The elements and their attributes that we want to validate.
     */
    private static final Map<String, String> ELEMENTS_TO_ATTRIBUTES;

    static {
        Map<String, String> elementsToAttributes = new HashMap<>();
        elementsToAttributes.put("a", "href");
        elementsToAttributes.put("frame", "src");
        elementsToAttributes.put("img", "src");
        ELEMENTS_TO_ATTRIBUTES = Collections.unmodifiableMap(elementsToAttributes);
    }

    private static final UrlValidator URL_VALIDATOR = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);

    /**
     * The file to start from. Links from the file will be checked. Non-URL links (i.e. local files) will be taken for
     * further link checking (feels like recursion)
     */
    @Parameter(required = true, property = "linkchecker.startFile")
    private File startFile;

    /**
     * The file name to be used as the default, in case a (non-URL) link points to a folder. It's what happens on a web
     * server: requests for {@code http://foo/bar} will serve you (typically) {@code http://foo/bar/index.html}.
     */
    @Parameter(required = false, property = "linkchecker.defaultFile", defaultValue = "index.html")
    private String defaultFile;

    /**
     * Should this plugin make your build fail if it encounters links to {@code localhost}. Typically, depending on
     * something local to the build would hamper the portability of the build
     */
    @Parameter(required = false, property = "linkchecker.failOnLocalHost", defaultValue = "true")
    private boolean failOnLocalHost;

    /**
     * Should this plugin make your build fail if it encounters bad URLs. This is not the default, in appreciation of
     * the fact that (non-local) URLs are out of our control. Typically, validating (non-local) URLs would hamper the
     * reproducibility of the build
     */
    @Parameter(required = false, property = "linkchecker.failOnBadUrls", defaultValue = "false")
    private boolean failOnBadUrls;

    /**
     * Should this plugin make your build fail altogether, or only report its findings.
     */
    @Parameter(required = false, property = "linkchecker.reportOnly", defaultValue = "false")
    private boolean reportOnly;

    /**
     * Skip this plugin execution.
     */
    @Parameter(required = false, property = "linkchecker.skip", defaultValue = "false")
    private boolean skip;

    private List<String> badLinks = new ArrayList<>();
    private List<String> todoListLinks = new ArrayList<>();
    private Multimap<String, File> linksToSourceFiles = HashMultimap.create();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if(skip) {
            getLog().info("Not checking links, skipping goal as configured");
            return;
        }
        getLog().info("Checking links for: " + startFile + " (recursively)");

        todoListLinks.add(startFile.getName());
        // don't use foreach as this is a growing list
        for (int i = 0; i < todoListLinks.size(); i++) {
            String link = todoListLinks.get(i);
            boolean isUrl = URL_VALIDATOR.isValid(link);
            if (isUrl) {
                processLinkAsUrl(link);
            } else {
                processLinkAsFile(link);
            }
        }

        printResults();
        failIfNecessary();
    }

    private void failIfNecessary() throws MojoFailureException {
        if(!badLinks.isEmpty()) {
            if(reportOnly) {
                getLog().warn("Not failing build for bad links as configured");
            } else {
                throw new MojoFailureException(badLinks.size() + " bad links (see the build log)");
            }
        }
    }

    /**
     * Print the results. Use {@code println} instead of {@link #getLog()} so that we can influence the formatting
     * across multiple lines
     */
    private void printResults() {
        System.out.println();
        if (badLinks.isEmpty()) {
            System.out.println("no bad links");
        } else {
            System.out.println(badLinks.size() + " bad links:");
            for (String badLink : badLinks) {
                System.out.println("\t" + badLink);
                Collection<File> sourceFiles = linksToSourceFiles.get(badLink);
                if (!sourceFiles.isEmpty()) {
                    System.out.println("\tbad link referenced from:");
                    for (File sourceFile : sourceFiles) {
                        System.out.println("\t\t" + sourceFile);
                    }
                }
            }
        }
        System.out.println();

        getLog().info("processed " + todoListLinks.size() + " files");
    }

    private void processLinkAsFile(String link) throws MojoExecutionException {
        File file = getFile(link);
        if (file.exists()) {
            processFileContents(file);
        } else {
            badLinks.add(link);
        }
    }

    private void processLinkAsUrl(String link) {
        try {
            URL url = new URL(link);
            if (isHttp(url)) {
                if (isLocalHost(url)) {
                    getLog().warn("URL for localhost indicates suspicious environment dependency: " + url);
                    addBadLocalHostIfConfigured(link);
                } else {
                    processValidUrl(link, url);
                }
            } else {
                getLog().warn("only http* supported; not handling URL: " + url);
            }
        } catch (MalformedURLException exception) {
            getLog().info("bad URL: " + link, exception);
            addBadUrlIfConfigured(link);
        }
    }

    private void processValidUrl(String link, URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                getLog().info("got response code " + responseCode + " for URL: " + url);
                addBadUrlIfConfigured(link);
            }
        } catch (ConnectException exception) {
            getLog().info("cannot connect to URL: " + url);
            addBadUrlIfConfigured(link);
        } catch (IOException exception) {
            getLog().info("problem with URL: " + url);
            addBadUrlIfConfigured(link);
        }
    }

    private boolean isHttp(URL url) {
        // note that this also matches https
        return url.getProtocol().startsWith("http");
    }

    private boolean isIgnored(String link) {
        return link.startsWith("javascript:") || link.startsWith("mailto:");
    }

    private boolean isLocalHost(URL url) {
        // note that this is ignoring 127.0.0.1 altogether, gotta draw a line somewhere
        return url.getHost().equals("localhost");
    }

    private void addBadLocalHostIfConfigured(String link) {
        if (failOnLocalHost) {
            badLinks.add(link);
        }
    }

    private void addBadUrlIfConfigured(String link) {
        if (failOnBadUrls) {
            badLinks.add(link);
        }
    }

    private File getFile(String link) {
        String linkWithoutFragment = link.replaceFirst("#.*", "");
        File file = new File(startFile.getParentFile(), linkWithoutFragment);
        if (file.isDirectory()) {
            file = new File(file, defaultFile);
        }
        return file;
    }

    private void processFileContents(File file) throws MojoExecutionException {
        Document document = getDocument(file);
        for (String elementName : ELEMENTS_TO_ATTRIBUTES.keySet()) {
            Elements elements = document.select(elementName);
            String attributeName = ELEMENTS_TO_ATTRIBUTES.get(elementName);
            for (Element element : elements) {
                String link = element.attr(attributeName);
                if (isIgnored(link)) {
                    getLog().debug("ignoring: " + link);
                } else {
                    if (todoListLinks.contains(link)) {
                        getLog().debug("already marked: " + link);
                    } else {
                        todoListLinks.add(link);
                    }
                    linksToSourceFiles.put(link, file);
                }
            }
        }
    }

    private Document getDocument(File file) throws MojoExecutionException {
        try {
            return Jsoup.parse(file, "UTF-8", "");
        } catch (IOException e) {
            throw new MojoExecutionException("file cannot be read: " + file);
        }
    }

}
