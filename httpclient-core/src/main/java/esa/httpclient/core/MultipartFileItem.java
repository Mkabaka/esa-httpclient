/*
 * Copyright 2020 OPPO ESA Stack Project
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
package esa.httpclient.core;

import esa.commons.Checks;

import java.io.File;

public class MultipartFileItem {

    private final String name;
    private final String filename;
    private final File file;
    private final String contentType;
    private final boolean isText;

    MultipartFileItem(String name,
                      String filename,
                      File file,
                      String contentType,
                      boolean isText) {
        Checks.checkNotNull(name, "Name of multipart must not be null");
        Checks.checkNotNull(file, "Multipart file must not be null");
        this.name = name;
        this.filename = filename;
        this.file = file;
        this.contentType = contentType;
        this.isText = isText;
    }

    public String name() {
        return name;
    }

    public String fileName() {
        return filename;
    }

    public File file() {
        return file;
    }

    public String contentType() {
        return contentType;
    }

    public boolean isText() {
        return isText;
    }
}
