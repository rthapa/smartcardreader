# Host Card Emulation Reader and ISO 7816-4 Reader

This project follows the footstep of Google's host card emulation reader example and adds the functionality to read smart cards with
ISO 7816-4 support.

### Smart Card File Structure

This project assumes that the smart card has the following file structure and does not need to authenticate to access any files.
When a Smart Card is approached the code looks for aid "F222222222" and selects the file by id "0001" and returns the hex data as string.

```
[ Master File ]
      |
      |___ [ AID - F222222222  (Dedicated file) ]
                   |
                   |___ [ File id - 0001 (Elementary File) ]

```

### TODO
 
Add a UI to navigate between files in the smart card and functions to read or write to file.

### Screenshots

<p>
<img  width="260px" src="/docs/menu.png">
<img  width="260px" src="/docs/scaning.png">
<img  width="260px" src="/docs/discovered.png">
</p>

### Testing
Tested with following  ISO 7816-4 compliant device
* [HCE](https://developer.android.com/samples/CardEmulation/index.html) Host Card Emulation
* MIFARE® Desfire Ev1 4K ISO Cards

### Acknowledgments

* [CardWerk](http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4.aspx) ISO 7816-4 Documentation 
* [HCE reader](https://developer.android.com/samples/CardReader/index.html) HCE reader example
* [HCE emulation](https://developer.android.com/samples/CardEmulation/index.html) HCE emulation example
