/* Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geb.content

import geb.Browser
import geb.Configuration
import geb.Module
import geb.error.RequiredPageValueNotPresent
import geb.navigator.Navigator
import geb.navigator.factory.NavigatorFactory
import geb.waiting.WaitTimeoutException

import static groovy.lang.Closure.DELEGATE_FIRST

class PageContentTemplate {

    final Browser browser
    final PageContentContainer owner
    final String name
    final PageContentTemplateParams params
    final Closure factory
    final NavigatorFactory navigatorFactory

    private cache = [:]

    PageContentTemplate(Browser browser, PageContentContainer owner, String name, Map<String, ?> params, Closure factory, NavigatorFactory navigatorFactory) {
        this.browser = browser
        this.owner = owner
        this.name = name
        this.params = new PageContentTemplateParams(this, params)
        this.factory = factory
        this.navigatorFactory = navigatorFactory
    }

    String toString() {
        "content template '$name' defined by $owner"
    }

    Configuration getConfig() {
        browser.config
    }

    def get(Object[] args) {
        params.cache ? fromCache(*args) : create(*args)
    }

    private create(Object[] args) {
        def createAction = {
            def factoryReturn = invokeFactory(*args)
            def creation = wrapFactoryReturn(factoryReturn, *args)
            if (params.required) {
                if (creation instanceof TemplateDerivedPageContent) {
                    creation.require()
                } else if (creation == null) {
                    throw new RequiredPageValueNotPresent(this, *args)
                }
            }
            creation
        }

        def wait = config.getWaitForParam(params.wait)
        if (wait) {
            try {
                wait.waitFor {
                    def element = createAction.call()
                    waitConditionWithDelegate(element).call(element) ? element : null
                }
            } catch (WaitTimeoutException e) {
                if (params.required) {
                    throw e
                }
                e.lastEvaluationValue
            }
        } else {
            createAction()
        }

        def waitCondition = config.getWaitConditionParam(params.waitCondition)
        if (waitCondition) {

        }
    }

    private Closure<?> waitConditionWithDelegate(Object delegate) {
        if (params.waitCondition) {
            Closure<?> waitCondition = params.waitCondition.clone()
            waitCondition.resolveStrategy = DELEGATE_FIRST
            waitCondition.delegate = delegate
            waitCondition
        } else {
            { -> true }
        }
    }

    private fromCache(Object[] args) {
        def argsHash = Arrays.deepHashCode(args)
        if (!cache.containsKey(argsHash)) {
            cache[argsHash] = create(*args)
        }
        cache[argsHash]
    }

    private invokeFactory(Object[] args) {
        factory.delegate = createFactoryDelegate(args)
        factory.resolveStrategy = DELEGATE_FIRST
        factory(*args)
    }

    private createFactoryDelegate(Object[] args) {
        new PageContentTemplateFactoryDelegate(this, args)
    }

    private wrapFactoryReturn(factoryReturn, Object[] args) {
        if (factoryReturn instanceof Module) {
            factoryReturn.init(this, args)
        }
        if (factoryReturn instanceof Navigator) {
            new TemplateDerivedPageContent(browser, this, factoryReturn, *args)
        } else {
            factoryReturn
        }
    }

}