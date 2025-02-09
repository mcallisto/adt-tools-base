/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef MEMORY_PROFILER_COMPONENT_H_
#define MEMORY_PROFILER_COMPONENT_H_

#include <unordered_map>

#include "internal_memory_service.h"
#include "memory_service.h"
#include "perfd/profiler_component.h"
#include "perfd/sessions/sessions_manager.h"

namespace profiler {

class MemoryProfilerComponent final : public ProfilerComponent {
 public:
  explicit MemoryProfilerComponent(const SessionsManager& sessions,
                                   Daemon::Utilities* utilities)
      : private_service_(sessions, &collectors_),
        public_service_(&private_service_, utilities, &collectors_) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., the agent).
  grpc::Service* GetInternalService() override { return &private_service_; }

 private:
  InternalMemoryServiceImpl private_service_;

  MemoryServiceImpl public_service_;

  // Mapping session id to MemoryCollector.
  std::unordered_map<int64_t, MemoryCollector> collectors_;
};

}  // namespace profiler

#endif  // MEMORY_PROFILER_COMPONENT_H_
