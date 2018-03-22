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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.BuildAuthorizationToken;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Pipeline step to trigger a build job on a remote Jenkins using a {@link BuildAuthorizationToken}.
 */
public class TriggerStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String job;
    private final String credentialsId;
    private final Map<String, String> parameters;
    private String jenkinsUrl;
    private Integer delay;

    public TriggerStep(String jenkinsUrl, String job, String credentialsId,
                       Map<String, String> parameters, Integer delay) {
        this.jenkinsUrl = TriggerCredentialsImpl.normalizeUrl(jenkinsUrl);
        this.job = job;
        this.credentialsId = credentialsId;
        this.parameters = parameters;
        this.delay = delay;
    }

    @DataBoundConstructor
    public TriggerStep(String job, String credentialsId, Map<String, String> parameters) {
        this.job = job;
        this.credentialsId = credentialsId;
        this.parameters = parameters == null ? new TreeMap<>() : new TreeMap<>(parameters);
    }

    public Integer getDelay() {
        return delay;
    }

    @DataBoundSetter
    public void setDelay(Integer delay) {
        this.delay = delay;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = TriggerCredentialsImpl.normalizeUrl(jenkinsUrl);
    }

    public String getJob() {
        return job;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public List<TriggerParameter> getParametersList() {
        List<TriggerParameter> result = new ArrayList<>(parameters.size());
        for (Map.Entry<String, String> entry : new TreeMap<>(parameters).entrySet()) {
            result.add(new TriggerParameter(entry));
        }
        return result;
    }

    public Map<String, String> getParameters() {
        return parameters.isEmpty() ? null : parameters;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(TaskListener.class, Run.class)));
        }

        @Override
        public String getFunctionName() {
            return "buildTokenTrigger";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.TriggerStep_DisplayName();
        }

        @Override
        public TriggerStep newInstance(@Nullable StaplerRequest req, @Nonnull JSONObject json)
                throws FormException {
            Map<String, String> parameters = new HashMap<>();
            Object parametersList = json.get("parametersList");
            if (req != null) {
                for (TriggerParameter p : req.bindJSONToList(TriggerParameter.class, parametersList)) {
                    if (!p.getKey().isEmpty()) {
                        parameters.put(p.getKey(), p.getValue());
                    }
                }
            } else {
                if (parametersList instanceof JSONObject) {
                    JSONObject j = (JSONObject) parametersList;
                    parameters.put(j.getString("key"), j.getString("value"));
                } else if (parametersList instanceof JSONArray) {
                    JSONArray a = (JSONArray) parametersList;
                    for (int i = 0; i < a.size(); i++) {
                        JSONObject j = a.getJSONObject(i);
                        parameters.put(j.getString("key"), j.getString("value"));
                    }
                }
            }
            String delayStr = json.getString("delay");
            Integer delay;
            if (StringUtils.isBlank(delayStr)) {
                delay = null;
            } else {
                try {
                    delay = Integer.valueOf(delayStr);
                    if (delay < 0) {
                        delay = null;
                    }
                } catch (NumberFormatException e) {
                    delay = null;
                }
            }
            return new TriggerStep(json.getString("jenkinsUrl"), json.getString("job"),
                    json.getString("credentialsId"), parameters, delay);
        }

        public FormValidation doCheckDelay(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }
            try {
                int delay = Integer.parseInt(value);
                if (delay < 0) {
                    return FormValidation.error("Quiet period cannot be a negative number");
                }
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckJob(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Must specify job to trigger");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckJenkinsUrl(@AncestorInPath Item owner,
                                                @QueryParameter String value)
                throws IOException {
            if (owner == null || !owner.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }
            if (StringUtils.isBlank(value)) {
                JenkinsLocationConfiguration cfg = JenkinsLocationConfiguration.get();
                String url = cfg == null ? null : cfg.getUrl();
                if (StringUtils.isBlank(url)) {
                    return FormValidation
                            .error("No Jenkins URL specified and this Jenkins has not been configured with a root URL"
                                    + " so cannot use that as a default");
                }
                return FormValidation.warningWithMarkup(
                        "Will assume <code>" + Util.xmlEscape(url) + "</code> as the Jenkins URL");
            }
            URL url = new URL(TriggerCredentialsImpl.normalizeUrl(value));
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            HttpURLConnection connection;
            if (proxy == null) {
                connection = (HttpURLConnection) url.openConnection();
            } else {
                connection = (HttpURLConnection) url.openConnection(proxy.createProxy(url.getHost()));
            }
            try {
                connection.getResponseCode();
                String version = connection.getHeaderField("X-Jenkins");
                if (StringUtils.isBlank(version)) {
                    return FormValidation.warningWithMarkup(
                            "Does not look like a Jenkins URL, expecting <code>X-Jenkins</code> header");
                }
                return FormValidation.okWithMarkup("Jenkins version: <code>" + Util.xmlEscape(version) + "</code>");
            } finally {
                connection.disconnect();
            }

        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item owner,
                                                     @QueryParameter("jenkinsUrl") String jenkinsUrl,
                                                     @QueryParameter String value) {
            if (owner == null || !owner.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel().add(value);
            }
            return CredentialsProvider.listCredentials(
                    IdCredentials.class,
                    owner,
                    owner instanceof Queue.Task
                            ? Tasks.getAuthenticationOf((Queue.Task) owner)
                            : ACL.SYSTEM,
                    URIRequirementBuilder.fromUri(jenkinsUrl).build(),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.instanceOf(TriggerCredentials.class),
                            CredentialsMatchers
                                    .withProperty("jenkinsUrl", TriggerCredentialsImpl.normalizeUrl(jenkinsUrl))
                    )
            );
        }
    }

    public static class Execution extends SynchronousStepExecution<Integer> {

        private static final long serialVersionUID = 1L;
        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final TriggerStep step;

        Execution(TriggerStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Integer run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            if (run == null) {
                throw new MissingContextVariableException(Run.class);
            }
            String jenkinsUrl = step.jenkinsUrl;
            if (StringUtils.isBlank(jenkinsUrl)) {
                // default to own
                JenkinsLocationConfiguration cfg = JenkinsLocationConfiguration.get();
                jenkinsUrl = cfg == null ? jenkinsUrl : cfg.getUrl();
            }
            if (StringUtils.isBlank(jenkinsUrl)) {
                throw new IOException("Could not determine Jenkins URL");
            }
            jenkinsUrl = TriggerCredentialsImpl.normalizeUrl(jenkinsUrl);
            TriggerCredentials credentials =
                    CredentialsProvider.findCredentialById(step.credentialsId, TriggerCredentials.class, run,
                            URIRequirementBuilder.fromUri(jenkinsUrl).build());
            if (credentials == null) {
                throw new CredentialNotFoundException(
                        "Could not find credentials entry with ID '" + step.credentialsId + "'");
            }
            if (!StringUtils.equals(jenkinsUrl, credentials.getJenkinsUrl())) {
                throw new CredentialNotFoundException(
                        "Credentials with ID '" + step.credentialsId + "' are for " + credentials.getJenkinsUrl()
                                + " not " + jenkinsUrl);
            }
            Secret secret = credentials.getPassword();
            String jobUrl = StringUtils.removeEnd(jenkinsUrl, "/")
                    + "/job/"
                    + StringUtils.removeStart(StringUtils.removeEnd(step.job, "/"), "/")
                    .replace("/", "/job/");
            TaskListener listener = getContext().get(TaskListener.class);
            assert listener != null;
            listener.getLogger()
                    .printf("[%tc] Triggering %s%n", new Date(), HyperlinkNote.encodeTo(jobUrl, step.job));
            String triggerUrl =
                    jenkinsUrl + "/buildByToken" + (step.parameters.isEmpty() ? "/build" : "/buildWithParameters");
            StringBuilder data = new StringBuilder();
            data.append("job=");
            data.append(URLEncoder.encode(step.job, "UTF-8"));
            data.append("&token=");
            data.append(URLEncoder.encode(secret.getPlainText(), "UTF-8"));
            if (step.delay != null && step.delay >= 0) {
                data.append("&delay=").append(step.delay);
            }
            for (Map.Entry<String, String> entry : step.parameters.entrySet()) {
                data.append("&");
                data.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                data.append("=");
                data.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            URL trigger = new URL(triggerUrl);
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            HttpURLConnection connection;
            if (proxy == null) {
                connection = (HttpURLConnection) trigger.openConnection();
            } else {
                connection = (HttpURLConnection) trigger.openConnection(proxy.createProxy(trigger.getHost()));
            }
            try {
                byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bytes);
                }
                int responseCode = connection.getResponseCode();
                listener.getLogger().printf("[%tc] Trigger returned HTTP/%d%n", new Date(), responseCode);
                if (responseCode == 404) {
                    throw new FileNotFoundException(String.format("%s for job %s", triggerUrl, step.job));
                }
                return responseCode;
            } finally {
                connection.disconnect();
            }
        }

    }
}
