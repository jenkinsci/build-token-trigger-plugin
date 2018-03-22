/*
 * The MIT License
 *
 * Copyright(c) 2018, Stephen Connolly.
 *
 * Permission is hereby granted,free of charge,to any person obtaining a copy
 * of this software and associated documentation files(the"Software"),to deal
 * in the Software without restriction,including without limitation the rights
 * to use,copy,modify,merge,publish,distribute,sublicense,and/or sell
 * copies of the Software,and to permit persons to whom the Software is
 * furnished to do so,subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED"AS IS",WITHOUT WARRANTY OF ANY KIND,EXPRESS OR
 * IMPLIED,INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,DAMAGES OR OTHER
 * LIABILITY,WHETHER IN AN ACTION OF CONTRACT,TORT OR OTHERWISE,ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.buildtokentrigger;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Default implementation of {@link TriggerCredentials}.
 */
public class TriggerCredentialsImpl extends BaseStandardCredentials implements TriggerCredentials {
    private final String jenkinsUrl;
    private final Secret password;

    @DataBoundConstructor
    public TriggerCredentialsImpl(CredentialsScope scope, String id,
                                  String description, String jenkinsUrl, String password) {
        super(scope, id, description);
        this.jenkinsUrl = normalizeUrl(jenkinsUrl);
        this.password = Secret.fromString(password);
    }

    /**
     * Fix an url.
     *
     * @param url the jenkins URL.
     * @return the normalized jenkins URL.
     */
    @NonNull
    public static String normalizeUrl(@CheckForNull String url) {
        url = StringUtils.defaultString(url);
        try {
            URI uri = new URI(url).normalize();
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                // we only expect http / https, but also these are the only ones where we know the authority
                // is server based, i.e. [userinfo@]server[:port]
                // DNS names must be US-ASCII and are case insensitive, so we force all to lowercase

                String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ENGLISH);
                int port = uri.getPort();
                if ("http".equals(scheme) && port == 80) {
                    port = -1;
                } else if ("https".equals(scheme) && port == 443) {
                    port = -1;
                }
                url = new URI(
                        scheme,
                        uri.getUserInfo(),
                        host,
                        port,
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment()
                ).toASCIIString();
            }
        } catch (URISyntaxException e) {
            // ignore, this was a best effort tidy-up
        }
        return url.replaceAll("/$", "");
    }

    @NonNull
    @Override
    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    @NonNull
    @Override
    public Secret getPassword() {
        return password;
    }

    /**
     * {@inheritDoc}
     */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.TriggerCredentialsImpl_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "icon-credentials-userpass";
        }

        public FormValidation doCheckJenkinsUrl(@AncestorInPath CredentialsStoreAction owner,
                                                @QueryParameter String value)
                throws IOException {
            try {
                String url = normalizeUrl(value);
                new URL(url);
                if (StringUtils.equals(value, url)) {
                    return FormValidation.ok();
                }
                return FormValidation.warningWithMarkup(
                        "Will be normalized to <code>" + Util.xmlEscape(url) + "</code>"
                );
            } catch (MalformedURLException e) {
                return FormValidation.error(e.getMessage());
            }
        }

    }
}
