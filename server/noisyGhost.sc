// @author Tim O'Brien
// @name noisyGhost.sc
// @desc Swarm-based synthesis, controlled/influenced by a beagleboard and sensors in a jar.

// QUICK INSTRUCTIONS: Select all and execute

//------------------------------------------------------
// For debugging
//------------------------------------------------------
//s.meter;
//OSCFunc.trace(true); // Turn posting on
//OSCFunc.trace(false); // Turn posting off
//SwingOSC.quitAll

s.boot;
s.meter;

// Synths
// FM synth (4 channel)
SynthDef(\simpFMAZ,
{ | outbus=0, freq=200, modfreq=290, modamp=0.5, amp=0.3, dur=0.5, pos=0 |
  var sig, mod, env, atk, dec, sus, rel;
  atk = dur*0.01;
  dec = dur*0.5;
  sus = dur*0.02;
  rel = dur*0.47;
  mod = SinOsc.ar(modfreq, 0, 0.5);
  sig = SinOsc.ar(freq + (freq * mod), 0, 1.0);
  env = Env([0, 1, 0.6, 0.4, 0]*amp, [atk, dec, sus, rel], curve: \squared);
  Out.ar(outbus,PanAz.ar(4,sig * EnvGen.kr(env, doneAction: 2), pos));
}).add;

// Conga drum sample synth (4 channel)
~paths = "/home/tim/EighthPlague_250A/EighthPlague/server/1302__ramjac__natal-quinto/*.aif".pathMatch;
~drumbufs = Array.newClear(~paths.size);
for (0, ~paths.size-1, {arg i; ~drumbufs[i] = Buffer.read(s,PathName(~paths[i]).asAbsolutePath);} );
SynthDef(\natalQuintoAz, { arg out = 0, bufnum, sampdur, amp=1, pos=0;
    var env;
    env = EnvGen.kr(Env.linen(sampdur*0.001,sampdur*0.9,sampdur*0.0999,amp),doneAction: 2);
    Out.ar( out,
        PanAz.ar(4, env * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum)), pos)
    );
}).add;

//------------------------------------------------------
// Receive notes from python swarm and make sound
~drums=true; // whether to use drums
~fm=true;    // whether to use FM synth

~scale = Scale.major;

// musical parameter mapping
~minFreq = 10;  // in midi notes
~maxFreq = 80; // in midi notes
~minAmp = 0;
~maxAmp = 0.5;
~minDur = 0.1;
~maxDur = 3.0;
~minPan = 0.0;
~maxPan = 2.0;
~minSpeedLim = 300.0;
~maxSpeedLim = 3000.0;
~rangeFreq = ~maxFreq - ~minFreq;
~rangeAmp = ~maxAmp - ~minAmp;
~rangeDur = ~maxDur - ~minDur;
~rangePan = ~maxPan - ~minPan;
~rangeSpeedLim = ~maxSpeedLim - ~minSpeedLim;

n = NetAddr("127.0.0.1", 57120); // local machine
OSCdef.newMatching(\incoming, {|msg, time, addr, recvPort| \matching.postln}, '/swarmNote', n); // path matching
a = OSCdef(\incomingNotePrint,
      {|msg, time, addr, recvPort|
      time.postln;   // post time
      msg.do({arg i; i.postln}); // post each part of the msg
      ~freq = ((~rangeFreq * msg[1]) + ~minFreq).round;
      ~amp = ((~rangeAmp * msg[2]) + ~minAmp);
      ~dur = ((~rangeDur * msg[3]) + ~minDur);
      ~pan = ((~rangePan * msg[4]) + ~minPan);
      //~pan2 = ((~rangePan * msg[4]) + ~minPan);
      ~degree = (~freq % 12) + (7 * ((~freq / 12).round - 6) );

      if (~fm,
          {Pbind(
            \instrument,  \simpFMAZ,
            \freq,        Pseq([~freq.midicps],1),
            \modfreq,     Pkey(\freq),
            \amp,         ~amp,
            \dur,         ~dur,
            \pos,         ~pan
          ).play;}
      );

      if (~drums,
          {Pbind(
            \instrument,  \natalQuintoAz,
            //\freq,       Pseq([msg[1].midicps.round],1),
            #[\bufnum, \sampdur],
                          Prand(Array.fill(~paths.size,{arg i; [~drumbufs[i],~drumbufs[i].duration]}),4.rand),
            \amp,         ~amp*4,
            \dur,         Prand([0.1,0.2,0.5,1], inf),
            \pos,         (~pan + 1.0) % 2.0
          ).play;}
      );


      },
      '/swarmNote',nil);

//---------------------------------------------------------
// Receive messages from jar and send to swarm
//---------------------------------------------------------
// /piezo float
// /accel float float float
// /jerk 1

OSCdef(\newAccelMsg,
      {|msg, time, addr, recvPort|
//       \Accel_Msg.postln;
//       time.postln;
//       msg.postln;
      m = NetAddr("127.0.0.1", 9000); // python
      //set attractor position
      m.sendMsg("/attr_dim",
            0.asInt, //freq dimension
            (msg[1]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            1.asInt, //amp dimension
            (msg[2]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            2.asInt, //dur dimension
            (msg[3]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            3.asInt, //pan dimension
            (msg[3]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            4.asInt, //modfreq dimension
            (msg[2]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            5.asInt, //ioi dimension
            (msg[3]*0.8).max(0).min(1)
      );
       
      },
      '/accel', nil);

OSCdef(\newJerkMsg,
      {|msg, time, addr, recvPort|
       \Jerk_Msg.postln;
       //time.postln;
       //addr.postln;
       //msg.postln;
       m = NetAddr("127.0.0.1", 9000); // python
       //set attractor position
       m.sendMsg("/resetboids");
      },
      '/jerk', nil);

OSCdef(\newPiezoMsg,
      {|msg, time, addr, recvPort|
       //\Piezo_Msg.postln;
       //time.postln;
       msg.postln;
       m = NetAddr("127.0.0.1", 9000); // python
       //set attractor position
       m.sendMsg("/speed",
             (msg[1]).max(0.3).min(1)
       );
      },
      '/piezo', nil);
