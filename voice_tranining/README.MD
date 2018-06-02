
# Create Adaptation 

https://cmusphinx.github.io/wiki/tutorialadapt/#creating-an-adaptation-corpus

## File to prepare

## make mfc file

```
sphinx_fe -argfile cn-zh-ptm/feat.params \
        -samprate 16000 -c arctic14.fileids \
       -di . -do . -ei wav -eo mfc -mswav yes
```

## Accumulating observation counts

```
/usr/local/libexec/sphinxtrain/bw \
 -hmmdir cn-zh-ptm \
 -moddeffn cn-zh-ptm/mdef \
 -ts2cbfn .ptm. \
 -feat s2_4x \
 -cmn current \
 -agc none \
 -dictfn zh_broadcastnews_utf8.dic \
 -ctlfn arctic14.fileids \
 -lsnfn arctic14.transcription \
 -accumdir .
```

## Creating a transformation with MLLR

```
/usr/local/libexec/sphinxtrain/mllr_solve \
    -meanfn cn-zh-ptm/means \
    -varfn cn-zh-ptm/variances \
    -outmllrfn mllr_matrix -accumdir .
```