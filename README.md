# VerbReplayer

Burp Extension

Inspired by https://dreyand.rs/code/review/2024/10/27/what-are-my-options-cyberpanel-v236-pre-auth-rce. Sits ontop of the proxy and will resend all traffic with the selected HTTP verb. Ideally used while browsing the site to get a sense of what authenticated actions accept alternative verbs.

Results are grouped by domain and by first part of the path. Requests can be sent to repeater for futher investigation. Highlight items to preserve them when clearing logs.

Results are not persistent, send anything useful to repeater before closing burp.
