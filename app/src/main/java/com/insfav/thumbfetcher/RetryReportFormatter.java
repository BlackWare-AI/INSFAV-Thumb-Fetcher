package com.insfav.thumbfetcher;

/** Formats retry reports without including cookies or other session data. */
final class RetryReportFormatter {
    static String format(String title, QueueStore.RetryReport report) {
        StringBuilder out=new StringBuilder(title).append(" — Completed\n\nRetried: ").append(report.retried)
            .append("\nRecovered: ").append(report.recovered).append("\nStill Failed: ").append(report.failed.size()).append("\n");
        for(String[] item:report.failed)out.append("\n").append(item[0]).append(" — ").append(item[1])
            .append("\nhttps://www.instagram.com/").append(item[0]).append("/\n");
        return out.toString();
    }
    private RetryReportFormatter() { }
}
