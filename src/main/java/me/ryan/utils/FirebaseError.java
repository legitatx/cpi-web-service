package me.legit.utils;

public enum FirebaseError {
    // Exchange custom token for an ID and refresh token errors
    INVALID_CUSTOM_TOKEN("The custom token format is incorrect or the token is invalid."),
    CREDENTIAL_MISMATCH("The custom token corresponds to a different Firebase project."),

    // Exchange a refresh token for an ID token errors
    TOKEN_EXPIRED("The user's credential is no longer valid. The user must sign in again."),
    USER_DISABLED("The user account has been disabled by an administrator."),
    USER_NOT_FOUND("There is no user record corresponding to this identifier. The user may have been deleted."),
    INVALID_REFRESH_TOKEN("An invalid refresh token is provided."),
    INVALID_GRANT_TYPE("The grant type specified is invalid."),
    MISSING_REFRESH_TOKEN("No refresh token provided."),

    // Sign up with email / password errors
    EMAIL_EXISTS("The email address is already in use by another account."),
    OPERATION_NOT_ALLOWED("Password sign-in is disabled for this project."),
    TOO_MANY_ATTEMPTS_TRY_LATER("We have blocked all requests from this device due to unusual activity. Try again later."),

    // Sign in with email / password errors
    EMAIL_NOT_FOUND("There is no user record corresponding to this identifier. The user may have been deleted."),
    INVALID_PASSWORD("The password is invalid or the user does not have a password."),

    // Verify password reset code errors
    EXPIRED_OOB_CODE("The action code has expired."),
    INVALID_OOB_CODE("The action code is invalid. This can happen if the code is malformed, expired, or has already been used."),

    // Change email errors
    INVALID_ID_TOKEN("The user's credential is no longer valid. The user must sign in again."),

    // Link with email/password errors
    CREDENTIAL_TOO_OLD_LOGIN_AGAIN("The user's credential is no longer valid. The user must sign in again."),
    WEAK_PASSWORD("The password must be 6 characters long or more."),

    // Other errors
    INVALID_EMAIL("The email address is invalid."),
    MISSING_PASSWORD("The password field is empty.");

    private String message;

    FirebaseError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
