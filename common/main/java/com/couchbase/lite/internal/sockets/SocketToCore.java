//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.sockets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * +------+                                                                      +--------+
 * |      | ==> SocketFromCore ==> AbstractCBLWebSocket ==>   SocketToCore   ==> |        |
 * | core |                                                                      | remote |
 * |      | <==  SocketToCore  <== AbstractCBLWebSocket <== SocketFromRemote <== |        |
 * +------+                                                                      +--------+
 */
public interface SocketToCore extends AutoCloseable {

    // use a single lock to avoid deadlocks
    @NonNull
    Object getLock();
    void init(@NonNull SocketFromCore listener);
    void ackOpenToCore(int httpStatus, @Nullable byte[] responseHeadersFleece);
    void ackWriteToCore(long byteCount);
    void writeToCore(@NonNull byte[] data);
    void requestCoreClose(@NonNull CloseStatus status);
    void closeCore(@NonNull CloseStatus status);
}
