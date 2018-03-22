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

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.util.AbstractMap;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

public class TriggerParameter extends AbstractDescribableImpl<TriggerParameter> implements Map.Entry<String, String> {
    private Map.Entry<String, String> entry;

    public TriggerParameter(Map.Entry<String, String> entry) {
        this.entry = entry;
    }

    @DataBoundConstructor
    public TriggerParameter(String key, String value) {
        this(new AbstractMap.SimpleEntry<>(key, value));
    }

    @Override
    public String getKey() {
        return entry.getKey();
    }

    @Override
    public String getValue() {
        return entry.getValue();
    }

    @Override
    public String setValue(String value) {
        return entry.setValue(value);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<TriggerParameter> {

    }
}
