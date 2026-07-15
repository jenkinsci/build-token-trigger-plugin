/*
 * The MIT License
 *
 * Copyright (c) 2026, Build Token Trigger Plugin contributors.
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
package org.jenkinsci.plugins.buildtokentrigger;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.AbstractProject;
import hudson.model.BuildAuthorizationToken;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.lang.reflect.Field;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TriggerStepIntegrationTest {

    @Test
    void triggersBuildOnSameJenkins(JenkinsRule j) throws Exception {
        FreeStyleProject target = j.createFreeStyleProject("target");
        setAuthToken(target, "secret");

        String jenkinsUrl = j.getURL().toString();
        SystemCredentialsProvider credentialsProvider = SystemCredentialsProvider.getInstance();
        credentialsProvider.getCredentials().add(new TriggerCredentialsImpl(
                CredentialsScope.GLOBAL, "self-token", null, jenkinsUrl, "secret"));
        credentialsProvider.save();

        WorkflowJob caller = j.jenkins.createProject(WorkflowJob.class, "caller");
        caller.setDefinition(new CpsFlowDefinition(
                "buildTokenTrigger credentialsId: 'self-token', jenkinsUrl: '" + jenkinsUrl
                        + "', job: 'target', parameters: [:], delay: 0",
                true));

        WorkflowRun callerBuild = j.buildAndAssertSuccess(caller);
        j.waitUntilNoActivity();

        FreeStyleBuild targetBuild = target.getLastBuild();
        assertNotNull(targetBuild);
        j.assertBuildStatusSuccess(targetBuild);
        j.assertLogContains("Job queued as", callerBuild);
    }

    private static void setAuthToken(FreeStyleProject project, String token) throws Exception {
        Field authToken = AbstractProject.class.getDeclaredField("authToken");
        authToken.setAccessible(true);
        authToken.set(project, new BuildAuthorizationToken(token));
        project.save();
    }
}
