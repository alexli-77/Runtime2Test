<div align="center">
  <h1>ProDJ</h1>
</div>

`ProDJ` is a tool for serializing Java objects to plain code.
It uses these capabilities to automatically generate test-cases from a
production workload.

See [Serializing Java Objects in Plain Code](http://arxiv.org/pdf/2405.11294) (Julian Wachter, Deepika Tiwari, Martin Monperrus and Benoit Baudry), Journal of Software and Systems, 2025.

```bibtex
@article{2405.11294,
 title = {Serializing Java Objects in Plain Code},
 journal = {Journal of Systems and Software},
 year = {2025},
 doi = {10.1016/j.jss.2025.112721},
 author = {Julian Wachter and Deepika Tiwari and Martin Monperrus and Benoit Baudry},
 url = {http://arxiv.org/pdf/2405.11294},
}
```

## Setup
The easiest way to get an executable version of `ProDJ` is to use the provided
`flake.nix`:
1. Enter a dev-shell using `nix develop`
2. Run `java -jar rockstofetch/target/rockstofetch.jar --statistics <config file>`.
   You can find example config files in `rockstofetch/src/test/resources/`.
