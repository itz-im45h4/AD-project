# Postman Assets

## Why this folder is important in the viva

Postman is the official backend client for this assignment. It demonstrates every REST boundary without needing a frontend. The collection stores non-sensitive URLs, request examples, and token variables; credentials and real tokens must never be exported.

The JSON files deliberately contain no `//` or `/* */` comments because Postman imports strict JSON. Their `description`, collection/folder names, and this README provide the explanatory documentation without breaking the import format.

Import `RideLink.postman_collection.json` and `RideLink.local.postman_environment.json`, then select the local environment.

Add requests in the order of the documented workflow and keep all secret values out of the exported environment. Use the `accessToken` variable for authenticated requests after login.

Before the demonstration, export a non-sensitive collection/environment that contains the successful end-to-end scenario and at least two negative scenarios. In the viva, explain that the `accessToken` variable is set after login and then sent as `Authorization: Bearer {{accessToken}}` to protected APIs.
