# Cert-Checker

[![Release](https://img.shields.io/github/v/release/jpwiedekopf/cert-checker)]([https://img.shields.io/github/v/release/jpwiedekopf/cert-checker](https://github.com/jpwiedekopf/cert-checker/releases/latest/))
[![Conveyor](https://img.shields.io/badge/Packaged_with-Conveyor-blue)](https://jpwiedekopf.github.io/cert-checker/download.html)
![GitHub Release Date](https://img.shields.io/github/release-date/jpwiedekopf/cert-checker)

This is a simple tool, written in Kotlin using the [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) framework 
and packaged using [Hydraulic Conveyor](https://conveyor.hydraulic.dev) to check up on the expiry and issuer of SSL certificates you
care about.

The app uses local storage to store your hosts, and cache the certificate details, so it can also be used to query hosts you have only 
restricted access to (endpoints that aren't reachable outside of certain networks).
