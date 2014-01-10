/*
 * Copyright 2012-2014 the original author or authors.
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

package griffon.pivot.support.adapters;

import griffon.core.CallableWithArgs;

/**
 * @author Andres Almiray
 * @since 2.0.0
 */
public class MenuButtonAdapter implements GriffonPivotAdapter, org.apache.pivot.wtk.MenuButtonListener {
    private CallableWithArgs<Void> menuChanged;

    public CallableWithArgs<Void> getMenuChanged() {
        return this.menuChanged;
    }


    public void setMenuChanged(CallableWithArgs<Void> menuChanged) {
        this.menuChanged = menuChanged;
    }


    public void menuChanged(org.apache.pivot.wtk.MenuButton arg0, org.apache.pivot.wtk.Menu arg1) {
        if (menuChanged != null) {
            menuChanged.call(arg0, arg1);
        }
    }

}
