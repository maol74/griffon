/*
 * Copyright 2008-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package griffon.plugins.domain

import griffon.core.test.GriffonUnitRule
import org.junit.Rule

class CountByMethodSpec extends PersistentMethodSpecSupport {
    @Rule
    public final GriffonUnitRule griffon = new GriffonUnitRule()

    void "Zero result when invoking countBy() on an empty dataset"() {
        expect:
        0 == griffonDomainHandler.countBy(Person, 'Name', 'Alice')
    }

    void "Non-zero result when invoking countBy() on full dataset (array version)"() {
        given:
        insertPeopleIntoDataset()

        expect:
        2 == griffonDomainHandler.countBy(Person, 'Name', 'Alice')
    }

    void "Non-zero result when invoking countBy() on full dataset (list version)"() {
        given:
        insertPeopleIntoDataset()

        expect:
        2 == griffonDomainHandler.countBy(Person, 'Name', ['Alice'])
    }

    void "Non-zero result when invoking countBy() on full dataset with composite operation"() {
        given:
        insertPeopleIntoDataset()

        expect:
        1 == griffonDomainHandler.countBy(Person, 'NameAndLastname', ['Alice', 'Elmerson'])
    }
}
