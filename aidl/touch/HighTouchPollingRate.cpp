/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.touch-service.onyx"

#include "HighTouchPollingRate.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>

#include <cerrno>
#include <climits>
#include <cstdlib>
#include <string>

using ::android::base::ReadFileToString;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

namespace aidl {
namespace vendor {
namespace lineage {
namespace touch {

namespace {

constexpr int kDefaultReportRate = 120;
constexpr int kHighReportRate = 240;

bool ParseReportRate(const std::string& raw, int* out_rate) {
    std::string trimmed = Trim(raw);
    if (trimmed.empty()) {
        return false;
    }

    // Kernel returns "report_rate: 120".
    std::string value = trimmed;
    size_t sep = trimmed.find(':');
    if (sep != std::string::npos) {
        value = Trim(trimmed.substr(sep + 1));
    }

    errno = 0;
    char* end = nullptr;
    long parsed = std::strtol(value.c_str(), &end, 10);
    if (errno != 0 || end == value.c_str() || *end != '\0') {
        return false;
    }
    if (parsed < INT_MIN || parsed > INT_MAX) {
        return false;
    }

    *out_rate = static_cast<int>(parsed);
    return true;
}

}  // namespace

ndk::ScopedAStatus HighTouchPollingRate::getEnabled(bool* _aidl_return) {
    std::string buf;
    if (!ReadFileToString("/proc/xm_htc_report_rate", &buf)) {
        LOG(ERROR) << "Failed to read current HighTouchPollingRate state";
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    int rate = 0;
    if (!ParseReportRate(buf, &rate)) {
        LOG(ERROR) << "Unexpected report_rate format: " << Trim(buf);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    *_aidl_return = rate > kDefaultReportRate;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus HighTouchPollingRate::setEnabled(bool enabled) {
    const std::string target_rate = enabled ? std::to_string(kHighReportRate)
                                            : std::to_string(kDefaultReportRate);
    if (!WriteStringToFile(target_rate, "/proc/xm_htc_report_rate")) {
        LOG(ERROR) << "Failed to write HighTouchPollingRate state";
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    return ndk::ScopedAStatus::ok();
}

}  // namespace touch
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
