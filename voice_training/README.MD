
# Create Adaptation 

https://cmusphinx.github.io/wiki/tutorialadapt/#creating-an-adaptation-corpus

## File to prepare

## make mfc file

```
sphinx_fe -argfile cn-zh-ptm/feat.params \
        -samprate 16000 -c arctic.fileids \
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
 -ctlfn arctic.fileids \
 -lsnfn arctic.transcription \
 -accumdir .
```

## Creating a transformation with MLLR

```
/usr/local/libexec/sphinxtrain/mllr_solve \
    -meanfn cn-zh-ptm/means \
    -varfn cn-zh-ptm/variances \
    -outmllrfn mllr_matrix -accumdir .
```

## Updating the acoustic model files with MAP

```
cp -a cn-zh-ptm cn-zh-ptm-adapt

/usr/local/libexec/sphinxtrain/map_adapt \
    -moddeffn cn-zh-ptm/mdef \
    -ts2cbfn .ptm. \
    -meanfn cn-zh-ptm/means \
    -varfn cn-zh-ptm/variances \
    -mixwfn cn-zh-ptm/mixture_weights \
    -tmatfn cn-zh-ptm/transition_matrices \
    -accumdir . \
    -mapmeanfn cn-zh-ptm-adapt/means \
    -mapvarfn cn-zh-ptm-adapt/variances \
    -mapmixwfn cn-zh-ptm-adapt/mixture_weights \
    -maptmatfn cn-zh-ptm-adapt/transition_matrices
```

```
/usr/local/libexec/sphinxtrain/mk_s2sendump \
    -pocketsphinx yes \
    -moddeffn cn-zh-ptm-adapt/mdef \
    -mixwfn cn-zh-ptm-adapt/mixture_weights \
    -sendumpfn cn-zh-ptm-adapt/sendump
```