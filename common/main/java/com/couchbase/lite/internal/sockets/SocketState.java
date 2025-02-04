//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.utils.StateMachine;


public enum SocketState {
    UNOPENED,               // Core state
    OPENING,                // Core state OPEN_REQUESTED
    OPEN,                   // Core state OPEN_COMPLETE
    CLOSING,                // Core state: CLOSE_REQUESTED
    CLOSED;                 // Core state: CLOSE_COMPLETED, terminal

    private static final StateMachine.Builder<SocketState> WS_STATE_BUILDER
        = new StateMachine.Builder<>(SocketState.class, LogDomain.NETWORK, UNOPENED, CLOSED)
        .addTransition(UNOPENED, OPENING, CLOSING)
        .addTransition(OPENING, OPEN, CLOSING)
        .addTransition(OPEN, CLOSING);

    @NonNull
    public static StateMachine<SocketState> getSocketStateMachine() { return WS_STATE_BUILDER.build(); }
}
