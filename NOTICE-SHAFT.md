# Pixiv-Shaft attribution

Pixiv XA contains adapted implementation ideas and API integration patterns from
[CeuiLiSA/Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft), including feed
pagination (`next_url`), comments, related/following/R18 endpoints, download state
presentation, and secure DNS fallback behavior.

Pixiv-Shaft is distributed under the MIT License. Copyright remains with its
respective contributors. XA's interface and application architecture are independently
implemented for this project.

XA keeps Android/OkHttp certificate and hostname verification enabled. Shaft's legacy
trust-all/no-SNI compatibility path is intentionally not enabled because it weakens TLS.
