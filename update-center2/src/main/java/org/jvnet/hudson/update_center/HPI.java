/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.update_center;

import com.sun.tools.javac.resources.version;
import net.sf.json.JSONObject;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.regex.*;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * A particular version of a plugin and its metadata.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class HPI implements IHPI {
    /**
     * Which of the lineage did this come from?
     */
    public final PluginHistory history;

    private final Pattern developersPattern = Pattern.compile("([^:]*):([^:]*):([^,]*),?");

    public HPI(PluginHistory history) throws AbstractArtifactResolutionException {
        this.history = history;
    }

    public abstract Attributes getManifestAttributes() throws IOException;

    /**
     * Who built this release?
     */
    public String getBuiltBy() throws IOException {
        return getManifestAttributes().getValue("Built-By");
    }

    /**
     * @deprecated
     *      Most probably you should be using {@link #getRequiredJenkinsVersion()}
     */
    public String getRequiredHudsonVersion() throws IOException {
        return getManifestAttributes().getValue("Hudson-Version");
    }

    public Version getRequiredJenkinsVersion() throws IOException {
        String v = getManifestAttributes().getValue("Jenkins-Version");
        if (v!=null)
            return new Version(v);

        v = getManifestAttributes().getValue("Hudson-Version");
        if (fixNull(v) != null) {
            try {
                VersionNumber n = new VersionNumber(v);
                if (n.compareTo(MavenRepositoryImpl.CUT_OFF)<=0)
                    return new Version(v);   // Hudson <= 1.395 is treated as Jenkins
                // TODO: Jenkins-Version started appearing from Jenkins 1.401 POM.
                // so maybe Hudson > 1.400 shouldn't be considered as a Jenkins plugin?
            } catch (IllegalArgumentException e) {
            }
        }

        // Parent versions 1.393 to 1.398 failed to record requiredCore.
        // If value is missing, let's default to 1.398 for now.
        return new Version("1.398");
    }

    /**
     * Earlier versions of the maven-hpi-plugin put "null" string literal, so we need to treat it as real null.
     */
    private static String fixNull(String v) {
        if("null".equals(v))    return null;
        return v;
    }

    public Version getCompatibleSinceVersion() throws IOException {
        return new Version(getManifestAttributes().getValue("Compatible-Since-Version"));
    }

    public String getDisplayName() throws IOException {
        return getManifestAttributes().getValue("Long-Name");
    }

    public String getSandboxStatus() throws IOException {
        return getManifestAttributes().getValue("Sandbox-Status");
    }

    public List<Dependency> getDependencies() throws IOException {
        String deps = getManifestAttributes().getValue("Plugin-Dependencies");
        if(deps==null)  return Collections.emptyList();

        List<Dependency> r = new ArrayList<Dependency>();
        for(String token : deps.split(","))
            r.add(new Dependency(token));
        return r;
    }

    public List<Developer> getDevelopers() throws IOException {
        String devs = getManifestAttributes().getValue("Plugin-Developers");
        if (devs == null || devs.trim().length()==0) return Collections.emptyList();

        List<Developer> r = new ArrayList<Developer>();
        Matcher m = developersPattern.matcher(devs);
        int totalMatched = 0;
        while (m.find()) {
            r.add(new Developer(m.group(1).trim(), m.group(2).trim(), m.group(3).trim()));
            totalMatched += m.end() - m.start();
        }
        if (totalMatched < devs.length())
            // ignore and move on
            System.err.println("Unparsable developer info: '" + devs.substring(totalMatched)+"'");
        return r;
    }



}
