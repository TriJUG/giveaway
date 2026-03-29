package dev.trijug.giveaway.api;

public record ApiResponse(int status, String body) {
    public boolean isNetworkError() {
        return status == -1;
    }

    public boolean notFound() {
        return status == 404;
    }

    public boolean ok() {
        return status >= 200 && status < 300;
    }
}
